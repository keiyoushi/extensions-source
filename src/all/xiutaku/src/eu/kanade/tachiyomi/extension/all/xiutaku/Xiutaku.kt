package eu.kanade.tachiyomi.extension.all.xiutaku

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.lib.randomua.UserAgentType
import keiyoushi.lib.randomua.setRandomUserAgent
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class Xiutaku : HttpSource() {

    override val name = "Xiutaku"

    override val baseUrl = "https://xiutaku.com"

    override val lang = "all"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 10, 1, TimeUnit.SECONDS)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .setRandomUserAgent(UserAgentType.MOBILE)

    // ========================= Popular =========================
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/hot?start=${20 * (page - 1)}", headers)

    override fun popularMangaParse(response: Response): MangasPage = parseMangasPage(response.asJsoup())

    private fun parseMangasPage(document: Document): MangasPage {
        val mangas = document.select(".blog > div").map(::mangaFromElement)
        val hasNextPage = document.selectFirst(".pagination-next:not([disabled])") != null
        return MangasPage(mangas, hasNextPage)
    }

    // ========================= Latest =========================
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/?start=${20 * (page - 1)}", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // ========================= Search =========================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val queryUrl = query.toHttpUrlOrNull()
        if (queryUrl != null && queryUrl.host == "xiutaku.com") {
            return GET(query, headers)
        }

        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addQueryParameter("search", query)
            addQueryParameter("start", (20 * (page - 1)).toString())
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        if (document.selectFirst(".article-header") != null) {
            val manga = mangaDetailsParse(document).apply {
                url = response.request.url.newBuilder().query(null).build().encodedPath
            }
            return MangasPage(listOf(manga), false)
        }

        return parseMangasPage(document)
    }

    // ========================= Details =========================
    override fun mangaDetailsParse(response: Response): SManga = mangaDetailsParse(response.asJsoup())

    private fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst(".article-header")?.text() ?: throw Exception("Title is mandatory")
        description = document.selectFirst(".article-info:not(:has(small))")?.text()
        genre = document.selectFirst(".article-tags")
            ?.select(".tags > .tag")?.joinToString { it.text().removePrefix("#") }
        status = SManga.COMPLETED
        initialized = true
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    // ========================= Chapters =========================
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val dateUploadStr = document.selectFirst(".article-info > small")?.text()?.removePrefix("🕒")
        val dateUpload = DATE_FORMAT.tryParse(dateUploadStr)
        val maxPage = document.selectFirst(".pagination-list > span:last-child > a")?.text()?.toIntOrNull() ?: 1
        val baseUrlString = response.request.url.toString().substringBefore("?")

        return (maxPage downTo 1).map { page ->
            SChapter.create().apply {
                url = "$baseUrlString?page=$page".removePrefix(baseUrl)
                name = "Page $page"
                date_upload = dateUpload
            }
        }
    }

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    // ========================= Pages =========================
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select(".article-fulltext img").mapIndexed { i, imgEl ->
            Page(i, imageUrl = imgEl.attr("abs:src"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ========================= Utilities =========================
    private fun mangaFromElement(element: Element) = SManga.create().apply {
        val link = element.selectFirst(".item-content .item-link") ?: throw Exception("Link is mandatory")
        title = link.text()
        thumbnail_url = element.selectFirst("img")?.attr("abs:src")
        url = link.attr("abs:href").removePrefix(baseUrl)
    }

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("HH:mm dd-MM-yyyy", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
}
