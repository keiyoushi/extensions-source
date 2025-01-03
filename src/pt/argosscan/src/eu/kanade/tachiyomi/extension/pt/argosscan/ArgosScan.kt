package eu.kanade.tachiyomi.extension.pt.argosscan

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
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
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

class ArgosScan : ParsedHttpSource() {

    override val name = "Argos Scan"

    override val baseUrl = "https://argoscomics.online"

    override val lang = "pt-BR"

    override val supportsLatest = false

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addInterceptor { chain ->
            val response = chain.proceed(chain.request())
            if (response.request.url.pathSegments.any { it.equals("pagina-de-login", true) }) {
                throw IOException("Faça login na WebView")
            }

            response
        }
        .build()

    // Website changed custom CMS.
    override val versionId = 3

    // ============================ Popular ======================================
    override fun popularMangaRequest(page: Int) = GET(baseUrl, headers)

    override fun popularMangaSelector() = ".card__main._grid:not(:has(a[href*=novel]))"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        with(element.selectFirst("h3.card__title")!!) {
            title = text()
            setUrlWithoutDomain(selectFirst("a")!!.absUrl("href"))
        }
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
    }

    override fun popularMangaNextPageSelector() = null

    // ============================ Latest ======================================

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()

    override fun latestUpdatesSelector() = throw UnsupportedOperationException()

    override fun latestUpdatesFromElement(element: Element) = throw UnsupportedOperationException()

    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException()

    // ============================ Search ======================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("s", query)
            .build()
        return GET(url, headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // ============================ Details =====================================

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1")!!.text()
        thumbnail_url = document.selectFirst("img.story__thumbnail-image")?.absUrl("src")
        description = document.selectFirst(".story__summary p")?.text()
        document.selectFirst(".story__status")?.let {
            status = when (it.text().trim().lowercase()) {
                "em andamento" -> SManga.ONGOING
                else -> SManga.UNKNOWN
            }
        }
        setUrlWithoutDomain(document.location())
    }

    // ============================ Chapter =====================================

    override fun chapterListSelector() = ".chapter-group__list li:has(a)"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        with(element.selectFirst("a")!!) {
            name = text()
            setUrlWithoutDomain(absUrl("href"))
        }
        element.selectFirst(".chapter-group__list-item-date")?.attr("datetime")?.let {
            date_upload = it.parseDate()
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        return super.chapterListParse(response).sortedByDescending(SChapter::chapter_number)
    }

    // ============================ Pages =======================================

    override fun pageListParse(document: Document): List<Page> {
        return document.select("#chapter-content img").mapIndexed { index, element ->
            Page(index, imageUrl = element.absUrl("src"))
        }
    }

    override fun imageUrlParse(document: Document) = ""

    // ============================== Utilities ==================================

    private fun String.parseDate(): Long {
        return try { dateFormat.parse(this.trim())!!.time } catch (_: Exception) { 0L }
    }
    companion object {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
    }
}
