package eu.kanade.tachiyomi.extension.en.todaymanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

open class TodayManga : ParsedHttpSource() {

    override val name = "TodayManga"

    override val baseUrl = "https://todaymanga.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/category/most-popular".addPage(page), headers)

    override fun popularMangaSelector(): String = "section div.serie"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("a[href]")!!.attr("abs:href"))
        thumbnail_url = element.selectFirst("img")!!.imgAttr()
        title = element.selectFirst("h2")!!.text()
    }

    override fun popularMangaNextPageSelector(): String =
        ".pagination > ul > li.active + li:has(a)"

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/category/recent".addPage(page), headers)

    override fun latestUpdatesSelector(): String = "ul.series > li"

    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        with(element.selectFirst("a[title][href]")!!) {
            setUrlWithoutDomain(attr("abs:href"))
            title = attr("title")
        }
        thumbnail_url = element.selectFirst("img")!!.imgAttr()
    }

    override fun latestUpdatesNextPageSelector(): String =
        popularMangaNextPageSelector()

    // =============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterList = if (filters.isEmpty()) getFilterList() else filters
        val categoryFilter = filterList.filterIsInstance<CategoryFilter>().first()
        val genreFilter = filterList.filterIsInstance<GenreFilter>().first()

        val url = baseUrl.toHttpUrl().newBuilder().apply {
            when {
                categoryFilter.state != 0 -> {
                    addPathSegment("category")
                    addPathSegments(categoryFilter.toUriPart())
                }
                genreFilter.state != 0 -> {
                    addPathSegment("genre")
                    addPathSegment(genreFilter.toUriPart())
                }
                query.isNotBlank() -> {
                    addPathSegment("search")
                    addQueryParameter("q", query)
                }
                else -> { // Default to popular
                    addPathSegments("category/most-popular")
                }
            }

            if (page > 1) {
                addQueryParameter("page", page.toString())
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaSelector(): String = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga =
        popularMangaFromElement(element)

    override fun searchMangaNextPageSelector(): String =
        popularMangaNextPageSelector()

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangaList = document.select(searchMangaSelector())
            .map(::searchMangaFromElement)
            .ifEmpty {
                document.select(latestUpdatesSelector())
                    .map(::latestUpdatesFromElement)
            }

        val hasNextPage = document.selectFirst(searchMangaNextPageSelector()) != null
        return MangasPage(mangaList, hasNextPage)
    }

    // =============================== Filters ==============================

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("Ignored when using text search"),
        Filter.Header("NOTE: Only one filter will be applied!"),
        Filter.Separator(),
        CategoryFilter(),
        GenreFilter(),
    )

    class CategoryFilter : UriPartFilter(
        "Category",
        arrayOf(
            Pair("<select>", ""),
            Pair("Most Popular Manga", "most-popular"),
            Pair("Highest Rated Manga", "highest-rated"),
            Pair("Trending This Week", "trending"),
            Pair("Recent Updated Manga", "recent"),
            Pair("Editors' Choices", "editor-pick"),
            Pair("Completed Comedy Manga", "completed-comedy-manga"),
            Pair("Completed Drama Manga", "completed-drama-manga"),
            Pair("Completed Fantasy Manga", "completed-fantasy-manga"),
            Pair("Completed Romance Manga", "completed-romance-manga"),
        ),
    )

    // The site doesn't seem to list all available genres, so instead the genres
    // were sampled from the first 5 pages of recently updated
    class GenreFilter : UriPartFilter(
        "Genre",
        arrayOf(
            Pair("<select>", ""),
            Pair("Action", "action"),
            Pair("Adventure", "adventure"),
            Pair("Comedy", "comedy"),
            Pair("Drama", "drama"),
            Pair("Ecchi", "ecchi"),
            Pair("Fantasy", "fantasy"),
            Pair("Gender Bender", "gender-bender"),
            Pair("Harem", "harem"),
            Pair("Historical", "historical"),
            Pair("Horror", "horror"),
            Pair("Martial Arts", "martial-arts"),
            Pair("Mature", "mature"),
            Pair("Music", "music"),
            Pair("Mystery", "mystery"),
            Pair("One Shot", "one-shot"),
            Pair("Psychological", "psychological"),
            Pair("Reverse Harem", "reverse-harem"),
            Pair("Romance", "romance"),
            Pair("School Life", "school-life"),
            Pair("Sci fi", "sci-fi"),
            Pair("Seinen", "seinen"),
            Pair("Shoujo", "shoujo"),
            Pair("Shounen Ai", "shounen-ai"),
            Pair("Shounen", "shounen"),
            Pair("Slice Of Life", "slice-of-life"),
            Pair("Sports", "sports"),
            Pair("Supernatural", "supernatural"),
            Pair("Tragedy", "tragedy"),
            Pair("Vampire", "vampire"),
            Pair("Webtoons", "webtoons"),
        ),
    )

    open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    // =========================== Manga Details ============================

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        with(document.selectFirst(".serie")!!) {
            title = selectFirst("h1")!!.text()
            thumbnail_url = selectFirst("img")!!.imgAttr()
            genre = select(".serie-info-head .tags > .tag-item").joinToString { it.text() }
            author = select(".authors a").joinToString { it.text() }
            status = selectFirst("li:contains(status) span").parseStatus()
        }

        description = buildString {
            val summary = document.selectFirst(".serie-summary")!!
            summary.childNodes().forEach { node ->
                if (node is TextNode) {
                    append(node.text())
                }
                if (node.nodeName() == "br") {
                    appendLine()
                }
            }
            summary.selectFirst("div[style]")?.also {
                append("\n\n")
                append(it.text())
            }
        }.trim()
    }

    private fun Element?.parseStatus(): Int = when (this?.text()?.lowercase()) {
        "complete" -> SManga.COMPLETED
        "on going" -> SManga.ONGOING
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request {
        val chapterHeaders = headersBuilder().apply {
            add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            set("Referer", baseUrl + manga.url)
        }.build()
        return GET("$baseUrl${manga.url}/chapter-list", chapterHeaders)
    }

    override fun chapterListSelector() = "ul.chapters-list > li"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        with(element.selectFirst("a")!!) {
            name = text()
            setUrlWithoutDomain(attr("abs:href"))
        }

        val dateText = element.selectFirst(".subtitle")?.text()
        date_upload = if (dateText == null) {
            0L
        } else if (dateText.contains("ago")) {
            dateText.parseRelativeDate()
        } else {
            parseDate(dateText)
        }
    }

    private fun parseDate(dateStr: String): Long {
        return try {
            dateFormat.parse(dateStr)!!.time
        } catch (_: ParseException) {
            0L
        }
    }

    // From OppaiStream
    private fun String.parseRelativeDate(): Long {
        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val relativeDate = this.split(" ").firstOrNull()
            ?.replace("one", "1")
            ?.replace("a", "1")
            ?.toIntOrNull()
            ?: return 0L

        when {
            "second" in this -> now.add(Calendar.SECOND, -relativeDate) // parse: 30 seconds ago
            "minute" in this -> now.add(Calendar.MINUTE, -relativeDate) // parses: "42 minutes ago"
            "hour" in this -> now.add(Calendar.HOUR, -relativeDate) // parses: "1 hour ago" and "2 hours ago"
            "day" in this -> now.add(Calendar.DAY_OF_YEAR, -relativeDate) // parses: "2 days ago"
            "week" in this -> now.add(Calendar.WEEK_OF_YEAR, -relativeDate) // parses: "2 weeks ago"
            "month" in this -> now.add(Calendar.MONTH, -relativeDate) // parses: "2 months ago"
            "year" in this -> now.add(Calendar.YEAR, -relativeDate) // parse: "2 years ago"
        }
        return now.timeInMillis
    }

    // =============================== Pages ================================

    override fun pageListParse(document: Document): List<Page> {
        return document.select(".chapter-content > img[data-index]").map { img ->
            val index = img.attr("data-index").toInt()
            val url = img.imgAttr()
            Page(index, imageUrl = url)
        }.sortedBy { it.index }
    }

    override fun imageUrlParse(document: Document) = ""

    override fun imageRequest(page: Page): Request {
        val imgHeaders = headersBuilder().apply {
            add("Accept", "image/avif,image/webp,*/*")
            add("Host", page.imageUrl!!.toHttpUrl().host)
        }.build()
        return GET(page.imageUrl!!, imgHeaders)
    }

    // ============================= Utilities ==============================

    private fun String.addPage(page: Int): HttpUrl {
        return this.toHttpUrl().newBuilder().apply {
            if (page > 1) {
                addQueryParameter("page", page.toString())
            }
        }.build()
    }

    private fun Element.imgAttr(): String = when {
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("data-src") -> attr("abs:data-src")
        else -> attr("abs:src")
    }
}
