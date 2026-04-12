package eu.kanade.tachiyomi.extension.en.hentaireadio

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class HentaiReadio : HttpSource() {

    override val name = "HentaiRead.io"

    override val baseUrl = "https://hentairead.io"

    override val lang = "en"

    override val supportsLatest = true

    // Site is behind Cloudflare
    override val client = network.cloudflareClient

    private val dateFormat by lazy {
        SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH)
    }

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/?act=search&f[status]=all&f[sortby]=top-manga&pageNum=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.card:has(.jtip)").map { element ->
            SManga.create().apply {
                val anchor = element.selectFirst(".title-manga a")!!
                title = anchor.text()
                setUrlWithoutDomain(anchor.attr("href"))
                thumbnail_url = element.selectFirst("img.card-img-top")?.absUrl("src")
            }
        }
        val hasNextPage = document.selectFirst("ul.pagination li.page-item a.page-link:contains(»)") != null
        return MangasPage(mangas, hasNextPage)
    }

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/?act=search&f[status]=all&f[sortby]=lastest-chap&pageNum=$page", headers)

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // =============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("act", "search")
            .addQueryParameter("pageNum", page.toString())

        if (query.isNotEmpty()) {
            url.addQueryParameter("f[keyword]", query)
        }

        var statusAdded = false
        var sortAdded = false

        filters.forEach { filter ->
            when (filter) {
                is StatusFilter -> {
                    url.addQueryParameter("f[status]", filter.toUriPart())
                    statusAdded = true
                }
                is SortFilter -> {
                    url.addQueryParameter("f[sortby]", filter.toUriPart())
                    sortAdded = true
                }
                is GenreFilter -> {
                    val genre = filter.toUriPart()
                    if (genre.isNotEmpty()) {
                        url.addQueryParameter("f[genres]", genre)
                    }
                }
                else -> {}
            }
        }

        if (!statusAdded) url.addQueryParameter("f[status]", "all")
        if (!sortAdded) url.addQueryParameter("f[sortby]", "lastest-chap")

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    // ============================== Details ===============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1.title-detail")!!.text()
            author = document.selectFirst(".author p.col-8")?.text()
                ?.takeUnless { it.contains("Updating", ignoreCase = true) }
            status = parseStatus(document.selectFirst(".status p.col-8")?.text())
            genre = document.select(".kind p.col-8 a").joinToString(", ") { it.text() }
            description = document.selectFirst("#summary_shortened")?.text()
            thumbnail_url = document.selectFirst(".col-image img")?.absUrl("src")
            initialized = true
        }
    }

    private fun parseStatus(status: String?) = when (status?.trim()?.lowercase()) {
        "complete", "completed" -> SManga.COMPLETED
        "in process", "ongoing" -> SManga.ONGOING
        "pause", "on hiatus" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("ul#list_chapter_id_detail li.wp-manga-chapter, ul.version-chap li.wp-manga-chapter")
            .map { element ->
                SChapter.create().apply {
                    val link = element.selectFirst("a")!!
                    setUrlWithoutDomain(link.attr("href"))
                    name = link.text()
                    date_upload = dateFormat.tryParse(
                        element.selectFirst(".chapter-release-date i")?.text()?.trim(),
                    )
                }
            }
    }

    // =============================== Pages ================================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select(".page-chapter img").mapIndexed { index, img ->
            // Prefer data-src (lazy-loaded) over src which may be a placeholder
            val url = img.absUrl("data-src").ifEmpty { img.absUrl("src") }
            Page(index, "", url)
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    // =============================== Filters ==============================

    override fun getFilterList() = FilterList(
        StatusFilter(),
        SortFilter(),
        Filter.Separator(),
        GenreFilter(),
    )

    private open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    private class StatusFilter :
        UriPartFilter(
            "Status",
            arrayOf(
                Pair("All", "all"),
                Pair("Completed", "complete"),
                Pair("Ongoing", "in-process"),
                Pair("Hiatus", "pause"),
            ),
        )

    private class SortFilter :
        UriPartFilter(
            "Sort By",
            arrayOf(
                Pair("Latest update", "lastest-chap"),
                Pair("Top all", "top-manga"),
                Pair("Hot", "hot"),
                Pair("New", "lastest-manga"),
                Pair("Top month", "top-month"),
                Pair("Top week", "top-week"),
                Pair("Top day", "top-day"),
                Pair("Follow", "follow"),
                Pair("Comment", "comment"),
                Pair("Num. Chapter", "num-chap"),
            ),
        )

    private class GenreFilter :
        UriPartFilter(
            "Genre",
            arrayOf(
                Pair("All genres", ""),
                Pair("Adult", "adult"),
                Pair("Action", "action"),
                Pair("Adaptation", "adaptation"),
                Pair("Adventure", "adventure"),
                Pair("Anime", "anime"),
                Pair("Comedy", "comedy"),
                Pair("Completed", "completed"),
                Pair("Cooking", "cooking"),
                Pair("Crime", "crime"),
                Pair("Crossdressing", "crossdressin"),
                Pair("Delinquents", "delinquents"),
                Pair("Demons", "demons"),
                Pair("Detective", "detective"),
                Pair("Drama", "drama"),
                Pair("Ecchi", "ecchi"),
                Pair("Fantasy", "fantasy"),
                Pair("Game", "game"),
                Pair("Ghosts", "ghosts"),
                Pair("Hentai", "hentai"),
                Pair("Harem", "harem"),
                Pair("Historical", "historical"),
                Pair("Horror", "horror"),
                Pair("Isekai", "isekai"),
                Pair("Josei", "josei"),
                Pair("Magic", "magic"),
                Pair("Magical", "magical"),
                Pair("Manhua", "manhua"),
                Pair("Manhwa", "manhwa"),
                Pair("Martial Arts", "martial-arts"),
                Pair("Mature", "mature"),
                Pair("Mecha", "mecha"),
                Pair("Medical", "medical"),
                Pair("Military", "military"),
                Pair("Moder", "moder"),
                Pair("Monsters", "monsters"),
                Pair("Music", "music"),
                Pair("Mystery", "mystery"),
                Pair("Office Workers", "office-workers"),
                Pair("One shot", "one-shot"),
                Pair("Philosophical", "philosophical"),
                Pair("Police", "police"),
                Pair("Reincarnation", "reincarnation"),
                Pair("Reverse", "reverse"),
                Pair("Reverse harem", "reverse-harem"),
                Pair("Romance", "romance"),
                Pair("Royal family", "royal-family"),
                Pair("Smut", "smut"),
                Pair("School Life", "school-life"),
                Pair("Sci-fi", "scifi"),
                Pair("Seinen", "seinen"),
                Pair("Shoujo", "shoujo"),
                Pair("Shoujo Ai", "shoujo-ai"),
                Pair("Shounen", "shounen"),
                Pair("Shounen Ai", "shounen-ai"),
                Pair("Slice of Life", "slice-of-life"),
                Pair("Sports", "sports"),
                Pair("Super power", "super-power"),
                Pair("Superhero", "superhero"),
                Pair("Supernatural", "supernatural"),
                Pair("Survival", "survival"),
                Pair("Thriller", "thriller"),
                Pair("Time Travel", "time-travel"),
                Pair("Tragedy", "tragedy"),
                Pair("Vampire", "vampire"),
                Pair("Villainess", "villainess"),
                Pair("Webtoons", "webtoons"),
                Pair("Yaoi", "yaoi"),
                Pair("Yuri", "yuri"),
                Pair("Zombies", "zombies"),
            ),
        )
}
