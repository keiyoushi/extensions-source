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
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy

class MangaSect : ParsedHttpSource() {

    override val name = "Manga Sect"

    override val baseUrl = "https://mangasect.com"

    override val lang = "en"

    override val supportsLatest = true

    private val json: Json by injectLazy()

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(1)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // Popular

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/ranking/week/$page", headers)

    override fun popularMangaSelector(): String = "div#main div.grid > div"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        thumbnail_url = element.selectFirst("img")?.imgAttr()
        element.selectFirst(".text-center a")!!.run {
            title = text().trim()
            setUrlWithoutDomain(attr("href"))
        }
    }

    override fun popularMangaNextPageSelector(): String = ".blog-pager > span.pagecurrent + span"

    // Latest

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/all-manga/$page/?sort=1", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun latestUpdatesSelector(): String =
        throw UnsupportedOperationException("Not used")

    override fun latestUpdatesFromElement(element: Element): SManga =
        throw UnsupportedOperationException("Not used")

    override fun latestUpdatesNextPageSelector(): String =
        throw UnsupportedOperationException("Not used")

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

        return GET(url.toString(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaSelector(): String =
        throw UnsupportedOperationException("Not used")

    override fun searchMangaFromElement(element: Element): SManga =
        throw UnsupportedOperationException("Not used")

    override fun searchMangaNextPageSelector(): String =
        throw UnsupportedOperationException("Not used")

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

    // Details

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        description = document.selectFirst("div#syn-target")?.text()
        thumbnail_url = document.selectFirst(".a1 > figure img")?.imgAttr()
        title = document.selectFirst(".a2 header h1")?.text()?.trim() ?: "N/A"
        genre = document.select(".a2 div > a[rel='tag'].label").joinToString(", ") { it.text() }

        document.selectFirst(".a1 > aside")?.run {
            author = select("div:contains(Authors) > span a")
                .joinToString(", ") { it.text().trim() }
                .takeUnless { it.isBlank() || it.equals("Updating", true) }
            status = selectFirst("div:contains(Status) > span")?.text().let(::parseStatus)
        }
    }

    private fun parseStatus(status: String?): Int = when {
        status.equals("ongoing", true) -> SManga.ONGOING
        status.equals("completed", true) -> SManga.COMPLETED
        status.equals("on-hold", true) -> SManga.ON_HIATUS
        status.equals("canceled", true) -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    // Chapters

    override fun chapterListSelector() = "ul > li.chapter"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        element.selectFirst("time[datetime]")?.also {
            date_upload = it.attr("datetime").toLongOrNull()?.let { it * 1000L } ?: 0L
        }
        element.selectFirst("a")!!.run {
            text().trim().also {
                name = it
                chapter_number = it.substringAfter("hapter ").toFloatOrNull() ?: 0F
            }
            setUrlWithoutDomain(attr("href"))
        }
    }

    // Pages

    override fun pageListRequest(chapter: SChapter): Request {
        val pageHeaders = headersBuilder().apply {
            add("Accept", "application/json, text/javascript, */*; q=0.01")
            add("Host", baseUrl.toHttpUrl().host)
            add("Referer", baseUrl + chapter.url)
            add("X-Requested-With", "XMLHttpRequest")
        }.build()

        val id = chapter.url.split("/").last()
        return GET("$baseUrl/ajax/image/list/chap/$id", pageHeaders)
    }

    @Serializable
    data class PageListResponseDto(val html: String)

    override fun pageListParse(response: Response): List<Page> {
        val data = response.parseAs<PageListResponseDto>().html
        return pageListParse(
            Jsoup.parseBodyFragment(
                data,
                response.request.header("Referer")!!,
            ),
        )
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.separator").map { page ->
            val index = page.attr("data-index").toInt()
            val url = page.selectFirst("a")!!.attr("abs:href")
            Page(index, document.location(), url)
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

    // Utilities

    // From mangathemesia
    private fun Element.imgAttr(): String = when {
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("data-src") -> attr("abs:data-src")
        else -> attr("abs:src")
    }

    private inline fun <reified T> Response.parseAs(): T {
        return json.decodeFromString(body.string())
    }
}
