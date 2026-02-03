package eu.kanade.tachiyomi.multisrc.comicaso

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

abstract class Comicaso(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
    private val pageSize: Int = 16,
    private val excludedGenres: Set<String> = emptySet(),
) : HttpSource() {

    override val supportsLatest = true

    override val client = network.cloudflareClient

    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    // Popular
    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/wp-json/neoglass/v1/mangas".toHttpUrl().newBuilder()
            .addQueryParameter("paged", page.toString())
            .addQueryParameter("per_page", pageSize.toString())
            .build()

        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<MangaListResponse>()
        val mangas = result.items.map { it.toSManga() }
        return MangasPage(mangas, result.items.size == pageSize)
    }

    // Latest
    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/v2/?page=$page"
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.ng-list div.ng-list-item").map { element ->
            SManga.create().apply {
                val thumb = element.selectFirst("div.ng-list-thumb")!!
                val link = thumb.selectFirst("a")!!.absUrl("href")
                title = element.selectFirst("div.ng-list-info a h3")!!.text()
                thumbnail_url = thumb.selectFirst("img")?.let { img ->
                    img.attr("abs:src").ifEmpty { img.attr("abs:data-src") }
                } ?: ""
                setUrlWithoutDomain(link)
            }
        }
        val hasNextPage = document.selectFirst("div.ng-pagination a.ng-page-btn.next") != null
        return MangasPage(mangas, hasNextPage)
    }

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/wp-json/neoglass/v1/mangas".toHttpUrl().newBuilder()
            .addQueryParameter("paged", page.toString())
            .addQueryParameter("per_page", pageSize.toString())

        if (query.isNotEmpty()) {
            url.addQueryParameter("s", query)
        }

        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> {
                    if (filter.state > 0) {
                        val genre = filter.values[filter.state]
                        val genreSlug = genreToSlug(genre)
                        url.addQueryParameter("genre", genreSlug)
                    }
                }

                is StatusFilter -> {
                    if (filter.state == 1) {
                        url.addQueryParameter("completed", "1")
                    }
                }

                else -> {}
            }
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = when {
        query.startsWith(URL_SEARCH_PREFIX) -> {
            val url = query.removePrefix(URL_SEARCH_PREFIX).trim().replace(baseUrl, "")
            val mangaUrl = if (url.startsWith("/")) url else "/$url"
            fetchMangaDetails(
                SManga.create().apply {
                    this.url = mangaUrl
                },
            )
                .map { MangasPage(listOf(it), false) }
        }

        query.startsWith("http://") || query.startsWith("https://") -> {
            val url = query.trim().replace(baseUrl, "")
            val mangaUrl = if (url.startsWith("/")) url else "/$url"
            fetchMangaDetails(
                SManga.create().apply {
                    this.url = mangaUrl
                },
            )
                .map { MangasPage(listOf(it), false) }
        }

        else -> super.fetchSearchManga(page, query, filters)
    }

    protected open fun genreToSlug(genre: String): String = when (genre) {
        "Action" -> "action"
        "Adaptation" -> "adaptation"
        "Adult" -> "adult"
        "Adventure" -> "adventure"
        "Age Gap" -> "age-gap"
        "Aliens" -> "aliens"
        "Animals" -> "animals"
        "Anthology" -> "anthology"
        "BDSM" -> "bdsm"
        "Beasts" -> "beasts"
        "Bloody" -> "bloody"
        "Bodyswap" -> "bodyswap"
        "Boys" -> "boys"
        "Cheating/Infidelity", "Cheating Infidelity" -> "cheating-infidelity"
        "Childhood Friends" -> "childhood-friends"
        "College life", "College Life" -> "college-life"
        "Comedy" -> "comedy"
        "Contest winning", "Contest Winning" -> "contest-winning"
        "Cooking" -> "cooking"
        "Crime" -> "crime"
        "Crossdressing" -> "crossdressing"
        "Delinquents" -> "delinquents"
        "Demons" -> "demons"
        "Drama" -> "drama"
        "Dungeons" -> "dungeons"
        "Ecchi" -> "ecchi"
        "Emperor's daughte", "Emperors Daughte" -> "emperors-daughte"
        "Fantasy" -> "fantasy"
        "Fetish" -> "fetish"
        "Fighting" -> "fighting"
        "Full Color" -> "full-color"
        "Game" -> "game"
        "Gender Bender" -> "gender-bender"
        "Genderswap" -> "genderswap"
        "Ghosts" -> "ghosts"
        "Girl" -> "girl"
        "Girls" -> "girls"
        "Gore" -> "gore"
        "Harem" -> "harem"
        "Hentai" -> "hentai"
        "Historical" -> "historical"
        "Horror", "Horrow" -> "horrow"
        "Incest" -> "incest"
        "Isekai" -> "isekai"
        "Josei(W)", "Joseiw" -> "joseiw"
        "Kids" -> "kids"
        "Magic" -> "magic"
        "Magical Girls" -> "magical-girls"
        "Manga" -> "manga"
        "Manhua" -> "manhua"
        "Manhwa" -> "manhwa"
        "Martial Arts" -> "martial-arts"
        "Mature" -> "mature"
        "Medical" -> "medical"
        "Military" -> "military"
        "Monster Girls" -> "monster-girls"
        "Monsters" -> "monsters"
        "Music" -> "music"
        "Mystery" -> "mystery"
        "NTR", "Ntr" -> "ntr"
        "Non-human", "Non Human" -> "non-human"
        "Office Workers" -> "office-workers"
        "Omegaverse" -> "omegaverse"
        "Oneshot" -> "oneshot"
        "Philosophical" -> "philosophical"
        "Police" -> "police"
        "Psychological" -> "psychological"
        "Regression" -> "regression"
        "Reincarnation" -> "reincarnation"
        "Revenge" -> "revenge"
        "Reverse Harem" -> "reverse-harem"
        "Reverse Isekai" -> "reverse-isekai"
        "Romance" -> "romance"
        "Royal family", "Royal Family" -> "royal-family"
        "Royalty" -> "royalty"
        "School Life" -> "school-life"
        "Sci-Fi", "Sci Fi" -> "sci-fi"
        "Seinen(M)", "Seinenm" -> "seinenm"
        "Sejarah" -> "sejarah"
        "Shoujo(G)", "Shoujog" -> "shoujog"
        "Shoujo ai", "Shoujo Ai" -> "shoujo-ai"
        "Shounen ai", "Shounen Ai" -> "shounen-ai"
        "Shounen(B)", "Shounenb" -> "shounenb"
        "Showbiz" -> "showbiz"
        "Slice of Life", "Slice Of Life" -> "slice-of-life"
        "SM/BDSM/SUB-DOM", "SM Bdsm Sub Dom" -> "sm-bdsm-sub-dom"
        "Smut" -> "smut"
        "Space" -> "space"
        "Sports" -> "sports"
        "Super Power" -> "super-power"
        "Superhero" -> "superhero"
        "Supernatural" -> "supernatural"
        "Survival" -> "survival"
        "Thriller" -> "thriller"
        "Time Travel" -> "time-travel"
        "Tower Climbing" -> "tower-climbing"
        "Tragedy" -> "tragedy"
        "Transmigration" -> "transmigration"
        "Vampires" -> "vampires"
        "Video Games" -> "video-games"
        "Villainess" -> "villainess"
        "Violence" -> "violence"
        "Western" -> "western"
        "Wuxia" -> "wuxia"
        "Yakuzas" -> "yakuzas"
        "Yaoi(BL)", "Yaoibl" -> "yaoibl"
        "Yuri(GL)", "Yurigl" -> "yurigl"
        "Zombies" -> "zombies"
        else -> genre.lowercase()
    }

    // Details
    override fun mangaDetailsRequest(manga: SManga): Request = GET("$baseUrl${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val requestUrl = response.request.url.toString()
        val mangaUrl = requestUrl.replace(baseUrl, "").let { if (it.startsWith("/")) it else "/$it" }
        return SManga.create().apply {
            url = mangaUrl
            title = document.selectFirst("h1.ng-detail-title")!!.text()

            description = buildString {
                document.selectFirst("p.ng-desc")?.text()?.let { append(it) }

                document.selectFirst(".ng-meta-info p:contains(Alternative:)")
                    ?.ownText()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let {
                        if (isNotEmpty()) append("\n\n")
                        append("Alternative: ")
                        append(it)
                    }
            }

            status = document.selectFirst(".ng-meta-info p:contains(Status:)")
                ?.ownText()
                ?.let { statusText ->
                    when {
                        statusText.contains("On-going", ignoreCase = true) -> SManga.ONGOING
                        statusText.contains("End", ignoreCase = true) -> SManga.COMPLETED
                        else -> SManga.UNKNOWN
                    }
                } ?: SManga.UNKNOWN

            genre = document.select(".ng-meta-row:contains(Genres:)")
                .text()
                .substringAfter("Genres:")
                .split(",")
                .mapNotNull { it.trim().takeIf { s -> s.isNotEmpty() } }
                .joinToString()

            thumbnail_url = document.selectFirst(".ng-detail-cover")
                ?.attr("style")
                ?.substringAfter("url('")
                ?.substringBefore("')") ?: ""
        }
    }

    // Chapters
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("ul.ng-chapter-list li.ng-chapter-item").map { element ->
            SChapter.create().apply {
                val link = element.selectFirst("a.ng-btn.ng-read-small")!!
                setUrlWithoutDomain(link.attr("href"))
                name = element.selectFirst(".ng-chapter-title")!!.text()
                date_upload = element.selectFirst(".ng-chapter-date")?.text()?.let { parseChapterDate(it) } ?: 0L
            }
        }.reversed()
    }

    private fun parseChapterDate(dateStr: String): Long {
        if (dateStr.isEmpty()) return 0L

        val calendar = Calendar.getInstance()

        return when {
            dateStr.contains("hari", ignoreCase = true) -> {
                val days = dateStr.replace(Regex("\\D"), "").toIntOrNull() ?: 1
                calendar.add(Calendar.DAY_OF_MONTH, -days)
                calendar.timeInMillis
            }

            dateStr.contains("minggu", ignoreCase = true) -> {
                val weeks = dateStr.replace(Regex("\\D"), "").toIntOrNull() ?: 1
                calendar.add(Calendar.WEEK_OF_YEAR, -weeks)
                calendar.timeInMillis
            }

            dateStr.contains("bulan", ignoreCase = true) -> {
                val months = dateStr.replace(Regex("\\D"), "").toIntOrNull() ?: 1
                calendar.add(Calendar.MONTH, -months)
                calendar.timeInMillis
            }

            else -> dateFormat.tryParse(dateStr)
        }
    }

    // Pages
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("div.ng-chapter-images div.ng-chapter-image img").mapIndexed { index, img ->
            val imageUrl = img.attr("abs:src").ifEmpty { img.attr("abs:data-src") }
            Page(index, document.location(), imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // Filter
    override fun getFilterList(): FilterList {
        val genres = ALL_GENRES.filterNot { it in excludedGenres }.toTypedArray()
        return FilterList(
            Filter.Header("NOTE: Filters are ignored when using text search!"),
            Filter.Separator(),
            GenreFilter(genres),
            StatusFilter(),
        )
    }

    protected class GenreFilter(genres: Array<String>) :
        Filter.Select<String>(
            "Genre",
            genres,
        )

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
