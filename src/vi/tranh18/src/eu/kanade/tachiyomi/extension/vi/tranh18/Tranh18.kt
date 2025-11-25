package eu.kanade.tachiyomi.extension.vi.tranh18

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.collections.sortedByDescending

class Tranh18 : ParsedHttpSource() {
    override val lang: String = "vi"

    override val name: String = "Tranh18"

    override val baseUrl: String = "https://tranh18z.com"

    override val supportsLatest: Boolean = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/update" + if (page > 1) "?page=$page" else "", headers)
    }

    override fun latestUpdatesSelector(): String = ".box-body ul li, .manga-list ul li"

    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        val sel = element.select(".mh-item, .manga-list-2-cover")
        setUrlWithoutDomain(sel.select("a").attr("href"))
        title = sel.select("a").attr("title")
        thumbnail_url = baseUrl + sel.select("p.mh-cover").attr("style")
            .substringAfter("url(")
            .substringBefore(")")
            .ifEmpty { baseUrl + sel.select("img").attr("data-original") }
    }

    override fun latestUpdatesNextPageSelector(): String = ".mt20"

    override fun popularMangaRequest(page: Int): Request {
        return GET(baseUrl, headers)
    }

    override fun popularMangaFromElement(element: Element): SManga = latestUpdatesFromElement(element)

    override fun popularMangaSelector(): String = latestUpdatesSelector()

    override fun popularMangaNextPageSelector(): String = latestUpdatesNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val infoMobile = document.select(".detail-main")
        title = document.select(".info h1").takeIf { it.isNotEmpty() }
            ?.text()
            ?: infoMobile.select(".detail-main-info-title").text()
        genre = document.select(".ticai:contains(Thể loại) a").takeIf { it.isNotEmpty() }
            ?.joinToString { it.text() }
            ?: infoMobile.select(".detail-main-info-author:contains(Thể loại) a").joinToString { it.text() }
        description = document.select("p.content").takeIf { it.isNotEmpty() }
            ?.joinToString("\n") { it.text() }
            ?: document.select("p.detail-desc").joinToString("\n") { it.wholeText() }
        author = document.select(".subtitle:contains(Tác giả：)").takeIf { it.isNotEmpty() }
            ?.text()?.removePrefix("Tác giả：")
            ?: infoMobile.select(".detail-main-info-author:contains(Tác giả：) a").text().removePrefix("Tác giả：")
        status = parseStatus(
            document.select(".block:contains(Trạng thái)").takeIf { it.isNotEmpty() }
                ?.text()
                ?: document.select(".detail-list-title-1").text(),
        )
        thumbnail_url = document.select(".banner_detail_form .cover img").attr("abs:src").ifEmpty {
            document.select(".detail-main-cover img").attr("data-original")
        }
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        listOf("Đang Tiến Hành", "Đang Cập Nhật").any { status.contains(it, ignoreCase = true) } -> SManga.ONGOING
        listOf("Hoàn Thành", "Đã Hoàn Thành", "Đã hoàn tất").any { status.contains(it, ignoreCase = true) } -> SManga.COMPLETED
        listOf("Tạm Ngưng", "Tạm Hoãn").any { status.contains(it, ignoreCase = true) } -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector(): String = "ul.detail-list-select li"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
        name = element.select("a").text()
        date_upload = System.currentTimeMillis()
        val number = Regex("""(\d+(?:\.\d+)*)""").find(name)?.value?.toFloatOrNull() ?: 0f
        chapter_number = number
    }

    override fun chapterListParse(response: Response): List<SChapter> =
        chapterListSelector()
            .let(response.asJsoup()::select)
            .map { element -> chapterFromElement(element) }
            .sortedByDescending { it.chapter_number }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    override fun pageListParse(document: Document): List<Page> {
        return document.select("img.lazy").mapIndexed { index, it ->
            val url = it.absUrl("data-original")
            val finalUrl = if (url.startsWith("https://external-content.duckduckgo.com/iu/")) {
                url.toHttpUrl().queryParameter("u")
            } else {
                url
            }
            Page(index, imageUrl = finalUrl)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addPathSegment("search")
                addQueryParameter("keyword", query)
            } else {
                latestUpdatesRequest(page)
            }
        }.build()
        return GET(url, headers)
    }

    override fun searchMangaFromElement(element: Element): SManga = latestUpdatesFromElement(element)

    override fun searchMangaSelector(): String = latestUpdatesSelector()

    override fun searchMangaNextPageSelector(): String = latestUpdatesNextPageSelector()
}
