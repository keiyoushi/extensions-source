package eu.kanade.tachiyomi.extension.en.mangabtt

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Calendar

class MangaBTT : ParsedHttpSource() {

    override val name = "MangaBTT"

    override val baseUrl = "https://manhwalampo.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client by lazy {
        network.cloudflareClient.newBuilder()
            .rateLimit(2)
            .build()
    }

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request = searchMangaRequest(
        page = page,
        query = "",
        filters = FilterList(
            SortByFilter(default = 2),
            StatusFilter(default = 1),
            GenreFilter(default = 1),
        ),
    )

    override fun popularMangaSelector(): String =
        searchMangaSelector()

    override fun popularMangaFromElement(element: Element): SManga =
        searchMangaFromElement(element)

    override fun popularMangaNextPageSelector(): String =
        searchMangaNextPageSelector()

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = searchMangaRequest(
        page = page,
        query = "",
        filters = FilterList(
            SortByFilter(default = 8),
            StatusFilter(default = 1),
            GenreFilter(default = 1),
        ),
    )

    override fun latestUpdatesSelector(): String =
        searchMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga =
        searchMangaFromElement(element)

    override fun latestUpdatesNextPageSelector(): String =
        searchMangaNextPageSelector()

    // =============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/find-story".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addQueryParameter("keyword", query)
            } else {
                val genre = filters.firstInstanceOrNull<GenreFilter>()?.selectedValue.orEmpty()
                val status = filters.firstInstanceOrNull<StatusFilter>()?.selectedValue.orEmpty()
                val sortBy = filters.firstInstanceOrNull<SortByFilter>()?.selectedValue.orEmpty()

                addQueryParameter("status", status)
                addQueryParameter("sort", sortBy)
                if (genre.isNotBlank()) {
                    addPathSegment(genre)
                }
            }

            addQueryParameter("page", page.toString())
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaSelector(): String = ".items > .row > .item"

    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        thumbnail_url = element.selectFirst(".image img")?.imgAttr()
        element.selectFirst("figcaption h3 a")!!.run {
            title = text()
            setUrlWithoutDomain(attr("abs:href"))
        }
    }

    override fun searchMangaNextPageSelector(): String =
        "ul.pagination > li.active + li:not(.disabled)"

    // =============================== Filters ==============================

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("Ignored when using text search"),
        Filter.Separator(),
        GenreFilter(),
        StatusFilter(),
        SortByFilter(),
    )

    // =========================== Manga Details ============================

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("h1.title-detail")!!.text()
        description = document.selectFirst(".detail-content p")?.text()
            ?.substringAfter("comic site. The Summary is ")

        document.selectFirst(".detail-info")?.run {
            thumbnail_url = selectFirst("img")?.imgAttr()
            status = selectFirst(".status p:not(.name)").parseStatus()
            genre = select(".kind a").joinToString(", ") { it.text() }
            author = selectFirst(".author p:not(.name)")?.text()?.takeUnless {
                it.equals("updating", true)
            }
        }
    }

    private fun Element?.parseStatus(): Int = with(this?.text()) {
        return when {
            equals("ongoing", true) -> SManga.ONGOING
            equals("Đang cập nhật", true) -> SManga.ONGOING
            equals("completed", true) -> SManga.COMPLETED
            equals("on-hold", true) -> SManga.ON_HIATUS
            equals("canceled", true) -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }

    // ============================== Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request {
        val postHeaders = headersBuilder().apply {
            add("Accept", "*/*")
            add("Host", baseUrl.toHttpUrl().host)
            add("Origin", baseUrl)
            set("Referer", baseUrl + manga)
            add("X-Requested-With", "XMLHttpRequest")
        }.build()

        val postBody = FormBody.Builder()
            .add("StoryID", manga.url.substringAfterLast("-"))
            .build()

        return POST("$baseUrl/Story/ListChapterByStoryID", postHeaders, postBody)
    }

    override fun chapterListSelector() = "ul > li:not(.heading)"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        element.selectFirst(".col-xs-4")?.also {
            date_upload = it.text().parseRelativeDate()
        }
        element.selectFirst("a")!!.run {
            name = text()
            setUrlWithoutDomain(attr("abs:href"))
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

        var parsedDate = 0L
        val relativeDate = this.split(" ").firstOrNull()
            ?.replace("one", "1")
            ?.replace("a", "1")
            ?.toIntOrNull()
            ?: return 0L

        when {
            // parse: 30 seconds ago
            "second" in this -> {
                parsedDate = now.apply { add(Calendar.SECOND, -relativeDate) }.timeInMillis
            }
            // parses: "42 minutes ago"
            "minute" in this -> {
                parsedDate = now.apply { add(Calendar.MINUTE, -relativeDate) }.timeInMillis
            }
            // parses: "1 hour ago" and "2 hours ago"
            "hour" in this -> {
                parsedDate = now.apply { add(Calendar.HOUR, -relativeDate) }.timeInMillis
            }
            // parses: "2 days ago"
            "day" in this -> {
                parsedDate = now.apply { add(Calendar.DAY_OF_YEAR, -relativeDate) }.timeInMillis
            }
            // parses: "2 weeks ago"
            "week" in this -> {
                parsedDate = now.apply { add(Calendar.WEEK_OF_YEAR, -relativeDate) }.timeInMillis
            }
            // parses: "2 months ago"
            "month" in this -> {
                parsedDate = now.apply { add(Calendar.MONTH, -relativeDate) }.timeInMillis
            }
            // parse: "2 years ago"
            "year" in this -> {
                parsedDate = now.apply { add(Calendar.YEAR, -relativeDate) }.timeInMillis
            }
        }
        return parsedDate
    }

    // =============================== Pages ================================

    override fun pageListParse(document: Document): List<Page> {
        return document.select(".reading-detail > .page-chapter").map { page ->
            val img = page.selectFirst("img[data-index]")!!
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

    private fun Element.imgAttr(): String = when {
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("data-src") -> attr("abs:data-src")
        else -> attr("abs:src")
    }
}
