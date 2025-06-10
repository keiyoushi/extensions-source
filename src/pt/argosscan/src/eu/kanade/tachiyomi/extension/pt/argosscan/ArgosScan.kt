package eu.kanade.tachiyomi.extension.pt.argosscan

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
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
            if (response.request.url.pathSegments.any { it.equals("login", true) }) {
                throw IOException("Faça login na WebView")
            }

            response
        }
        .build()

    // Website changed custom CMS.
    override val versionId = 3

    // ============================ Popular ======================================
    override fun popularMangaRequest(page: Int) = GET(baseUrl, headers)

    override fun popularMangaSelector() = throw UnsupportedOperationException()
    override fun popularMangaFromElement(element: Element) = throw UnsupportedOperationException()
    override fun popularMangaNextPageSelector() = null

    override fun popularMangaParse(response: Response): MangasPage {
        val script = response.asJsoup().selectFirst("script:containsData(projects)")!!.data()

        val mangas = POPULAR_REGEX.find(script)?.groups?.get(1)?.value?.let {
            it.parseAs<List<MangaDto>>()
                .filter { it.type != "novel" }
                .map { it.toSManga(baseUrl) }
        } ?: throw IOException("Não foi possivel encontrar os mangás")

        return MangasPage(mangas, false)
    }

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
        with(document) {
            title = selectFirst(".content h2")!!.text()
            thumbnail_url = selectFirst(".trailer-box img")?.absUrl("src")
            description = selectFirst(".content p")?.text()
            selectFirst("section[data-status]")?.attr("data-status")?.let {
                status = it.toStatus()
            }
            genre = select("h6:contains(Tags) + h6 > span").joinToString { it.text() }
        }
        setUrlWithoutDomain(document.location())
    }

    // ============================ Chapter =====================================

    override fun chapterListSelector() = ".manga-chapter"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        name = element.selectFirst("h5")!!.text()
        element.selectFirst("h6")?.let {
            date_upload = dateFormat.tryParse(it.text())
        }
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
    }

    // ============================ Pages =======================================

    override fun pageListParse(document: Document): List<Page> {
        return document.select(".manga-page img").mapIndexed { index, element ->
            Page(index, imageUrl = element.absUrl("src"))
        }
    }

    override fun imageUrlParse(document: Document) = ""

    // ============================== Utilities ==================================

    companion object {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT)
        val POPULAR_REGEX = """projects\s?=\s+([^;]+)""".toRegex()
    }
}
