package eu.kanade.tachiyomi.extension.all.manga18fx

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Locale

// Similar to Madara, but not really
@Source
abstract class Manga18fx : KeiSource() {
    private val chapterDateFormat = DateTimeFormatter.ofPattern("dd MMM yy", Locale.ENGLISH)

    // Popular
    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/hot-manga".toHttpUrl().newBuilder()
            .apply { if (page != 1) addQueryParameter("page", page.toString()) }
            .build()
        val document = client.get(url).asJsoup()
        return mangaParse(document)
    }

    // Latest
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get("$baseUrl/page/$page").asJsoup()
        return mangaParse(document)
    }

    // Search
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotEmpty()) {
            val fixedQuery = query
                .replace('\'', '’')
            val url = "$baseUrl/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", fixedQuery)
                .apply { if (page != 1) addQueryParameter("page", page.toString()) }
                .build()
            return mangaParse(client.get(url).asJsoup())
        }

        val genreFilter = filters.firstInstance<GenreFilter>()
        val genreOrderByFilter = filters.firstInstance<OrderByFilter>()
        val selectedGenre = genreFilter.vals[genreFilter.state].value
        val selectedGenreOrderBy = genreOrderByFilter.vals[genreOrderByFilter.state].value

        if (selectedGenre != null) {
            val url = baseUrl.toHttpUrl().newBuilder()
                .addPathSegment("manga-genre")
                .addPathSegment(selectedGenre)
                .apply { if (page != 1) addPathSegment(page.toString()) }
                .apply { if (selectedGenreOrderBy != null) addQueryParameter("orderby", selectedGenreOrderBy) }
                .build()
            return mangaParse(client.get(url).asJsoup())
        }

        error("Select a genre to search")
    }

    private fun mangaParse(document: Document): MangasPage {
        val mangas = document
            .selectFirst(".site-body > .bixbox:last-child")
            ?.select(".page-item")
            ?.mapNotNull(::mangaFromElement)
            ?: emptyList()

        val hasNextPage = document.selectFirst("#blog-pager li.next:not(.disabled)") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromElement(element: Element): SManga? = SManga.create().apply {
        // Matches both browse and "You may also like"
        val mangaLink = element.selectFirst(".tt > a")!!
        val mangaCover = element.selectFirst("a img[data-src]")
        setUrlWithoutDomain(mangaLink.absUrl("href"))
        title = mangaLink.text()
        thumbnail_url = mangaCover?.absUrl("src")

        if (lang == "en" && title.endsWith(" Raw")) return null
    }

    // Updates
    override suspend fun fetchMangaUpdate(manga: SManga, chapters: List<SChapter>, fetchDetails: Boolean, fetchChapters: Boolean): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(mangaDetailsParse(document), chapterListParse(document))
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val document = client.get(url).asJsoup()
        return mangaDetailsParse(document).apply { initialized = true }
    }

    private fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        setUrlWithoutDomain(document.location())
        title = document.selectFirst(".post-title > h1")!!.text()
        thumbnail_url = document.selectFirst(".summary_image img")?.absUrl("src")
        author = document.select(".author-content > a").joinToString { it.text() }.takeIf { it != "Updating" }
        artist = document.select(".artist-content > a").joinToString { it.text() }.takeIf { it != "Updating" }
        status = document.selectFirst(".summary-heading:has(h5:contains(Status)) + .summary-content")?.text().toStatus()

        description = buildString {
            document.selectFirst("#averagerate")?.text()?.also { rate ->
                document.selectFirst("#countrate")?.text()?.also { rateCount ->
                    if (isNotEmpty()) appendLine()
                    append("Rating: ")
                    appendLine(getRatingString(rate, rateCount.toIntOrNull() ?: 0))
                }
            }

            document
                .selectFirst(".list-comments > h4")
                ?.text()
                ?.split(' ')
                ?.firstOrNull()
                ?.toIntOrNull()
                ?.also { if (isNotEmpty()) appendLine() }
                ?.also { append("Comments: ") }
                ?.also { appendLine(it) }

            document
                .selectFirst(".sumbmrk")
                ?.text()
                ?.split(' ')
                ?.firstOrNull()
                ?.toIntOrNull()
                ?.also { if (isNotEmpty()) appendLine() }
                ?.also { append("Bookmarks: ") }
                ?.also { appendLine(it) }

            document
                .selectFirst(".dsct")
                ?.wholeText()
                ?.trim()
                ?.also { if (isNotEmpty()) appendLine() }
                ?.also { appendLine(it) }

            document
                .selectFirst(".summary-heading:has(h5:contains(Alternative)) + .summary-content")
                ?.text()
                ?.split(" / ")
                ?.takeIf { it.firstOrNull() != "N/A" }
                ?.also { if (isNotEmpty()) appendLine() }
                ?.also { appendLine("Alternative titles:") }
                ?.forEach { appendLine("- $it") }
        }

        val type = document.selectFirst(".summary-heading:has(h5:contains(Type)) + .summary-content")?.text()
        val genresContent = document.select(".genres-content > a").map { it.text() }
        genre = (listOfNotNull(type) + genresContent)
            .distinct()
            .joinToString()

        val relatedMangas = runCatching {
            document
                .selectFirst(".related-manga")
                ?.select(".item")
                ?.mapNotNull(::mangaFromElement)
                ?.map { it.toRelatedItem() }
                ?: emptyList()
        }.getOrElse { emptyList() }
        memo = buildJsonObject {
            put("relatedMangas", relatedMangas.toJsonElement())
        }
    }

    private fun String?.toStatus() = when (this) {
        "Ongoing" -> SManga.ONGOING
        "Completed" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    private fun chapterListParse(document: Document): List<SChapter> = document
        .select("#chapterlist li.a-h")
        .map(::chapterFromElement)

    private fun chapterFromElement(element: Element): SChapter {
        val chapterLink = element.selectFirst("a.chapter-name")!!
        val chapterDateStr = element.selectFirst(".chapter-time")?.text()
        return SChapter.create().apply {
            setUrlWithoutDomain(chapterLink.absUrl("href"))
            name = chapterLink.text()
            date_upload = chapterDateFormat.tryParseDate(chapterDateStr)
        }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        if (chapter.url.startsWith(baseUrl)) {
            // Legacy Madara URL is absolute and has suffix `?style=list`
            return chapter.url
        }
        return super.getChapterUrl(chapter)
    }

    // Pages
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)
        val document = client.get(chapterUrl).asJsoup()
        return parsePages(document)
    }

    private fun parsePages(document: Document): List<Page> = document
        .select(".page-break > img")
        .mapIndexed { index, element ->
            Page(index, imageUrl = element.absUrl("src"))
        }

    // Related
    override val supportsRelatedMangas = true
    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = manga
        .memo["relatedMangas"]
        ?.parseAs<List<RelatedManga>>()
        ?.map { it.toSManga() }
        ?: emptyList()

    @Serializable
    class RelatedManga(
        val url: String,
        val title: String,
        val thumbnail_url: String?,
    )

    private fun RelatedManga.toSManga(): SManga = SManga.create().apply {
        setUrlWithoutDomain(this@toSManga.url)
        title = this@toSManga.title
        thumbnail_url = this@toSManga.thumbnail_url
    }

    private fun SManga.toRelatedItem() = RelatedManga(url, title, thumbnail_url)

    // Filters
    override fun getFilterList(data: JsonElement?) = FilterList(
        buildList {
            add(Filter.Header("Filters do not apply to text search"))
            add(
                GenreFilter(
                    listOf(
                        Genre("<select>", null),
                        Genre("Adult", "adult"),
                        Genre("Drama", "drama"),
                        Genre("Harem", "harem"),
                        Genre("Seinen", "seinen"),
                        Genre("School", "school-life"),
                        Genre("Mature", "mature"),
                        Genre("Psychological", "psychological"),
                        Genre("Romance", "romance"),
                        Genre("Tragedy", "tragedy"),
                        Genre("Ecchi", "ecchi"),
                        Genre("Sci", "sci-fi"),
                        Genre("Yaoi", "yaoi"),
                        Genre("Action", "action"),
                        Genre("Martial", "martial-arts"),
                        Genre("Smut", "smut"),
                        Genre("Sports", "sports"),
                        Genre("Comedy", "comedy"),
                        Genre("Yuri", "yuri"),
                        Genre("Slice", "slice-of-life"),
                        Genre("Manhwa", "manhwa"),
                        Genre("Manhua", "manhua"),
                        Genre("Fantasy", "fantasy"),
                        Genre("Horror", "horror"),
                        Genre("Supernatural", "supernatural"),
                        Genre("Hentai", "hentai"),
                        Genre("Isekai", "isekai"),
                        Genre("Shoujo", "shoujo"),
                        Genre("Adventure", "adventure"),
                        Genre("Shounen", "shounen"),
                        Genre("Mecha", "mecha"),
                        Genre("Mystery", "mystery"),
                        Genre("Thriller", "thriller"),
                        Genre("Historical", "historical"),
                        Genre("Josei", "josei"),
                        Genre("Gender", "gender-bender"),
                        Genre("Webtoons", "webtoons"),
                        Genre("RPG", "rpg"),
                        Genre("GL", "gl"),
                        Genre("BL", "bl"),
                        Genre("Raw", "raw"),
                        Genre("Reincarnation", "reincarnation"),
                        Genre("Zombie", "zombie"),
                        Genre("Magic", "magic"),
                        Genre("Comics", "comics"),
                        Genre("Webtoon", "webtoon"),
                        Genre("Cooking", "cooking"),
                        Genre("NTR", "ntr"),
                        Genre("Doujinshi", "doujinshi"),
                        Genre("Game", "game"),
                        Genre("Vanilla", "vanilla"),
                        Genre("Demons", "demons"),
                        Genre("Family", "family"),
                        Genre("Super", "super-power"),
                        Genre("Uncensored", "uncensored-manhwa"),
                    ),
                ),
            )
            add(
                OrderByFilter(
                    listOf(
                        OrderBy("Default", null),
                        OrderBy("Latest", "latest"),
                        OrderBy("Rating", "rating"),
                        OrderBy("Most Views", "views"),
                    ),
                ),
            )
        },
    )

    class GenreFilter(val vals: List<Genre>) : Filter.Select<String>("Genre", vals.map { it.name }.toTypedArray())

    class Genre(val name: String, val value: String?)

    class OrderByFilter(val vals: List<OrderBy>) : Filter.Select<String>("Order by", vals.map { it.name }.toTypedArray())

    class OrderBy(val name: String, val value: String?)

    // Other
    // From ManhwaRead
    private fun getRatingString(rate: String, rateCount: Int): String {
        val ratingValue = rate.toDoubleOrNull() ?: 0.0
        val ratingStar = when {
            ratingValue >= 4.75 -> "★★★★★"
            ratingValue >= 4.25 -> "★★★★✬"
            ratingValue >= 3.75 -> "★★★★☆"
            ratingValue >= 3.25 -> "★★★✬☆"
            ratingValue >= 2.75 -> "★★★☆☆"
            ratingValue >= 2.25 -> "★★✬☆☆"
            ratingValue >= 1.75 -> "★★☆☆☆"
            ratingValue >= 1.25 -> "★✬☆☆☆"
            ratingValue >= 0.75 -> "★☆☆☆☆"
            ratingValue >= 0.25 -> "✬☆☆☆☆"
            else -> "☆☆☆☆☆"
        }
        return if (ratingValue > 0.0) {
            buildString {
                append(ratingStar, " ", rate)
                if (rateCount > 0) {
                    append(" (", rateCount, ")")
                }
            }
        } else {
            ""
        }
    }
}
