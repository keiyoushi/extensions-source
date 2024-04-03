package eu.kanade.tachiyomi.extension.en.mangasect

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class MangaSect : ParsedHttpSource() {

    override val name = "Manga Sect"

    override val baseUrl = "https://mangasect.net"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(1)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return "$baseUrl/all-manga/$page/".toHttpUrl().newBuilder()
            .addQueryParameter("sort", "views_week")
            .build()
            .let { GET(it, headers) }
    }

    override fun popularMangaSelector(): String = "div.row div.item div.image"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        thumbnail_url = element.selectFirst("img")?.imgAttr()
        element.selectFirst("a")!!.run {
            title = (attr("title"))
            setUrlWithoutDomain(attr("href"))
        }
    }

    override fun popularMangaNextPageSelector(): String = ".blog-pager > span.pagecurrent + span"

    // Latest

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/all-manga/$page/?sort=1", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun latestUpdatesSelector(): String =
        throw UnsupportedOperationException()

    override fun latestUpdatesFromElement(element: Element): SManga =
        throw UnsupportedOperationException()

    override fun latestUpdatesNextPageSelector(): String =
        throw UnsupportedOperationException()

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addPathSegment("search")
                addQueryParameter("keyword", query)
            } else {
                addPathSegment("filter")
                filters.forEach { filter ->
                    when (filter) {
                        is GenreFilter -> {
                            if (filter.checked.isNotEmpty()) {
                                addQueryParameter("genres", filter.checked.joinToString(","))
                            }
                        }
                        is StatusFilter -> {
                            if (filter.selected.isNotBlank()) {
                                addQueryParameter("status", filter.selected)
                            }
                        }
                        is SortFilter -> {
                            addQueryParameter("sort", filter.selected)
                        }
                        is ChapterCountFilter -> {
                            addQueryParameter("chapter_count", filter.selected)
                        }
                        is GenderFilter -> {
                            addQueryParameter("sex", filter.selected)
                        }
                        else -> {}
                    }
                }
            }

            addPathSegment(page.toString())
            addPathSegment("")
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaSelector(): String =
        throw UnsupportedOperationException()

    override fun searchMangaFromElement(element: Element): SManga =
        throw UnsupportedOperationException()

    override fun searchMangaNextPageSelector(): String =
        throw UnsupportedOperationException()

    // Filters

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("Ignored when using text search"),
        Filter.Separator(),
        GenreFilter(),
        ChapterCountFilter(),
        GenderFilter(),
        StatusFilter(),
        SortFilter(),
    )

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        description = document.selectFirst("div.detail-content > p")?.text()
        thumbnail_url = document.selectFirst(".col-image img")?.attr("src")
        title = document.selectFirst("h1.title-detail")?.text()?.trim() ?: "N/A"
        genre = document.select(".kind .col-xs-8 a").joinToString(", ") { it.text().trim() }
        author = document.select(".author .col-xs-8 a").joinToString(", ") { it.text().trim() }
        status = parseStatus(document.select(".status .col-xs-8").text().trim())
    }

    private fun parseStatus(status: String?): Int = when {
        status.equals("ongoing", true) -> SManga.ONGOING
        status.equals("completed", true) -> SManga.COMPLETED
        status.equals("on-hold", true) -> SManga.ON_HIATUS
        status.equals("canceled", true) -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    // Chapters

    override fun chapterListSelector() = "div.list-chapter ul > li.row"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val date = element.selectFirst("div.col-xs-4.text-center.no-wrap.small")?.text().orEmpty()
        date_upload = parseRelativeDate(date)
        element.selectFirst("div.col-xs-5.chapter a")?.run {
            text().trim().also {
                name = it
                chapter_number = it.substringAfter("Chapter ").toFloatOrNull() ?: 0F
            }
            setUrlWithoutDomain(attr("href"))
        }
    }

    // Pages

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.page-chapter > img").mapIndexed { index, img ->
            val url = img.attr("abs:data-original")
            Page(index, "", url)
        }
    }

    override fun imageUrlParse(document: Document) = ""
    fun parseRelativeDate(dateText: String): Long {
        val currentTime = System.currentTimeMillis()
        val matchResult = Regex("(\\d+)h ago").find(dateText)
        val hoursAgo = matchResult?.groups?.get(1)?.value?.toLongOrNull() ?: return 0L

        return currentTime - hoursAgo * 3600 * 1000
    }
    override fun imageRequest(page: Page): Request {
        val imgHeaders = headersBuilder().apply {
            add("Accept", "image/avif,image/webp,*/*")
            add("Host", page.imageUrl!!.toHttpUrl().host)
        }.build()
        return GET(page.imageUrl!!, imgHeaders)
    }

    // From mangathemesia
    private fun Element.imgAttr(): String = when {
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("data-src") -> attr("abs:data-src")
        else -> attr("abs:src")
    }
}
