package eu.kanade.tachiyomi.multisrc.comicaso

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable

abstract class Comicaso(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
    private val pageSize: Int = 16,
    private val excludedGenres: Set<String> = emptySet(),
) : HttpSource() {

    override val supportsLatest = true

    override val client = network.cloudflareClient

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    private fun indexRequest(page: Int, query: String = "", filters: FilterList? = null): Request {
        val url = "$baseUrl/wp-content/static/manga/index.json".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("q", query)

        filters?.forEach { filter ->
            when (filter) {
                is GenreFilter -> {
                    if (filter.state > 0) {
                        url.addQueryParameter("genre", filter.values[filter.state])
                    }
                }
                is StatusFilter -> {
                    if (filter.state == 1) {
                        url.addQueryParameter("completed", "1")
                    }
                }
                else -> Unit
            }
        }

        return GET(url.build(), headers)
    }

    private fun parseIndex(response: Response): List<MangaDto> = response.parseAs()

    private fun applyIndexFilters(
        all: List<MangaDto>,
        query: String,
        genre: String,
        completedOnly: Boolean,
    ): List<MangaDto> {
        var result = all

        if (query.isNotBlank()) {
            result = result.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.alternative.contains(query, ignoreCase = true)
            }
        }

        if (genre.isNotBlank() && genre != "All") {
            result = result.filter { manga ->
                manga.genres.any { it.equals(genre, ignoreCase = true) }
            }
        }

        if (completedOnly) {
            result = result.filter {
                it.status.equals("completed", ignoreCase = true) ||
                    it.status.equals("end", ignoreCase = true)
            }
        }

        return result
    }

    private fun paged(list: List<MangaDto>, page: Int): MangasPage {
        val from = (page - 1) * pageSize
        if (from >= list.size) return MangasPage(emptyList(), false)
        val to = minOf(from + pageSize, list.size)
        val pageItems = list.subList(from, to).map { it.toSManga() }
        return MangasPage(pageItems, to < list.size)
    }

    // Popular
    override fun popularMangaRequest(page: Int): Request = indexRequest(page)

    override fun popularMangaParse(response: Response): MangasPage {
        val page = response.request.url.queryParameter("page")?.toIntOrNull() ?: 1
        val sorted = parseIndex(response).sortedByDescending { it.updated_at ?: 0L }
        return paged(sorted, page)
    }

    // Latest
    override fun latestUpdatesRequest(page: Int): Request = indexRequest(page)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val page = response.request.url.queryParameter("page")?.toIntOrNull() ?: 1
        val sorted = parseIndex(response).sortedByDescending { it.updated_at ?: 0L }
        return paged(sorted, page)
    }

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = indexRequest(page, query, filters)

    override fun searchMangaParse(response: Response): MangasPage {
        val page = response.request.url.queryParameter("page")?.toIntOrNull() ?: 1
        val query = response.request.url.queryParameter("q").orEmpty()
        val genre = response.request.url.queryParameter("genre").orEmpty()
        val completedOnly = response.request.url.queryParameter("completed") == "1"
        val filtered = applyIndexFilters(parseIndex(response), query, genre, completedOnly)
            .sortedByDescending { it.updated_at ?: 0L }
        return paged(filtered, page)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = when {
        query.startsWith(URL_SEARCH_PREFIX) -> {
            val url = query.removePrefix(URL_SEARCH_PREFIX).trim().replace(baseUrl, "")
            val mangaUrl = if (url.startsWith("/")) url else "/$url"
            fetchMangaDetails(SManga.create().apply { this.url = mangaUrl })
                .map { MangasPage(listOf(it), false) }
        }

        query.startsWith("http://") || query.startsWith("https://") -> {
            val url = query.trim().replace(baseUrl, "")
            val mangaUrl = if (url.startsWith("/")) url else "/$url"
            fetchMangaDetails(SManga.create().apply { this.url = mangaUrl })
                .map { MangasPage(listOf(it), false) }
        }

        else -> client.newCall(searchMangaRequest(page, query, filters))
            .asObservableSuccess()
            .map { response ->
                searchMangaParse(response)
            }
    }

    // Details
    override fun mangaDetailsRequest(manga: SManga): Request = GET("$baseUrl${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val title = document.selectFirst("h1")?.text().orEmpty()

        fun infoValue(label: String): String? {
            val row = document.select("*:matchesOwn(^\\s*$label\\s*$)").firstOrNull() ?: return null
            val parentText = row.parent()?.text().orEmpty()
            return parentText.substringAfter(label, "").trim().takeIf { it.isNotBlank() }
        }

        return SManga.create().apply {
            url = response.request.url.toString().replace(baseUrl, "")
            this.title = title

            val altTitle = document.selectFirst(".mjv2-manga-alt, .mjv2-manga-subtitle")?.text()

            description = buildString {
                document.selectFirst(".mjv2-synopsis p, .mjv2-synopsis-content p")?.text()?.let {
                    if (it.isNotBlank()) append(it)
                }

                altTitle?.takeIf { it.isNotBlank() && it != this@apply.title }?.let {
                    if (isNotEmpty()) append("\n\n")
                    append("Alternative: ")
                    append(it)
                }
            }

            status = when {
                infoValue("Status")?.contains("On Going", ignoreCase = true) == true -> SManga.ONGOING
                infoValue("Status")?.contains("Completed", ignoreCase = true) == true -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }

            genre = document.select(".mjv2-tags .mjv2-tag").joinToString { it.text() }
                .ifBlank { null }

            thumbnail_url = document.selectFirst(".mjv2-detail-cover img")
                ?.let { it.attr("abs:src").ifEmpty { it.attr("abs:data-src") } }
                .orEmpty()

            author = infoValue("Author")
            artist = infoValue("Artist")
        }
    }

    // Chapters
    override fun chapterListRequest(manga: SManga): Request {
        val slug = manga.url.trim('/').substringAfterLast('/')
        return GET("$baseUrl/wp-content/static/manga/$slug.json", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val manga = response.parseAs<MangaDetailsDto>()
        return manga.chapters.map { chapter ->
            SChapter.create().apply {
                setUrlWithoutDomain("/komik/${manga.slug}/${chapter.slug}/")
                name = chapter.title
                chapter_number = chapterNumberFrom(chapter.slug)
                date_upload = chapter.date * 1000L
            }
        }
            .distinctBy { it.url }
            .sortedByDescending { it.chapter_number }
    }

    private fun chapterNumberFrom(slug: String): Float {
        val decimal = Regex("(\\d+)\\s*[._-]\\s*(\\d+)")
            .find(slug)
            ?.groupValues
            ?.let { groups ->
                val main = groups.getOrNull(1).orEmpty()
                val sub = groups.getOrNull(2).orEmpty()
                if (main.isNotEmpty() && sub.isNotEmpty()) "$main.$sub" else null
            }
            ?.toFloatOrNull()

        val integer = Regex("(\\d+)")
            .find(slug)
            ?.groupValues
            ?.getOrNull(1)
            ?.toFloatOrNull()

        return decimal ?: integer ?: -1f
    }

    // Pages
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("figure img, .mjv2-reader img").mapIndexed { index, img ->
            val imageUrl = img.attr("abs:src").ifEmpty { img.attr("abs:data-src") }
            Page(index, document.location(), imageUrl)
        }.filter { it.imageUrl?.isNotBlank() == true }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // Filter
    override fun getFilterList(): FilterList {
        val genres = ALL_GENRES.filterNot { it in excludedGenres }.toTypedArray()
        return FilterList(
            Filter.Header("NOTE: Filters are applied locally from index.json"),
            Filter.Separator(),
            GenreFilter(genres),
            StatusFilter(),
        )
    }

    protected class GenreFilter(genres: Array<String>) : Filter.Select<String>("Genre", genres)

    companion object {
        const val URL_SEARCH_PREFIX = "url:"

        private val ALL_GENRES = arrayOf(
            "All",
            "Action",
            "Adaptation",
            "Adult",
            "Adventure",
            "Age Gap",
            "Aliens",
            "Animals",
            "Anthology",
            "BDSM",
            "Beasts",
            "Bloody",
            "Bodyswap",
            "Boys",
            "Cheating/Infidelity",
            "Childhood Friends",
            "College life",
            "Comedy",
            "Contest winning",
            "Cooking",
            "Crime",
            "Crossdressing",
            "Delinquents",
            "Demons",
            "Drama",
            "Dungeons",
            "Ecchi",
            "Emperor's daughte",
            "Fantasy",
            "Fetish",
            "Fighting",
            "Full Color",
            "Game",
            "Gender Bender",
            "Genderswap",
            "Ghosts",
            "Girl",
            "Girls",
            "Gore",
            "Harem",
            "Hentai",
            "Historical",
            "Horror",
            "Incest",
            "Isekai",
            "Josei(W)",
            "Kids",
            "Magic",
            "Magical Girls",
            "Manga",
            "Manhua",
            "Manhwa",
            "Martial Arts",
            "Mature",
            "Medical",
            "Military",
            "Monster Girls",
            "Monsters",
            "Music",
            "Mystery",
            "Non-human",
            "NTR",
            "Office Workers",
            "Omegaverse",
            "Oneshot",
            "Philosophical",
            "Police",
            "Psychological",
            "Regression",
            "Reincarnation",
            "Revenge",
            "Reverse Harem",
            "Reverse Isekai",
            "Romance",
            "Royal family",
            "Royalty",
            "School Life",
            "Sci-Fi",
            "Seinen(M)",
            "Sejarah",
            "Shoujo ai",
            "Shoujo(G)",
            "Shounen ai",
            "Shounen(B)",
            "Showbiz",
            "Slice of Life",
            "SM/BDSM/SUB-DOM",
            "Smut",
            "Space",
            "Sports",
            "Super Power",
            "Superhero",
            "Supernatural",
            "Survival",
            "Thriller",
            "Time Travel",
            "Tower Climbing",
            "Traditional Games",
            "Tragedy",
            "Transmigration",
            "Vampires",
            "Video Games",
            "Villainess",
            "Violence",
            "Virtual Reality",
            "Western",
            "Wuxia",
            "Yakuzas",
            "Yaoi(BL)",
            "Yuri(GL)",
            "Zombies",
        )
    }

    protected class StatusFilter :
        Filter.Select<String>(
            "Status",
            arrayOf("All", "Completed"),
        )
}
