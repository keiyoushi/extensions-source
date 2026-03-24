package eu.kanade.tachiyomi.extension.pt.argosscan

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class ArgosScan : ParsedHttpSource() {

    override val name = "Argos Scan"

    override val baseUrl = "https://argoscomics.online"

    override val lang = "pt-BR"

    override val supportsLatest = false

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .build()

    // Website changed custom CMS.
    override val versionId = 3

    // ============================ Popular ======================================
    override fun popularMangaRequest(page: Int) = GET(baseUrl, headers)

    override fun popularMangaSelector() = "#projects-container .manga-block"
    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        title = element.selectFirst("h3")!!.text()
        thumbnail_url = element.selectFirst("img")!!.absUrl("src")
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
    }
    override fun popularMangaNextPageSelector() = null

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        if (document.select("a[href*='auth/discord']").isNotEmpty()) {
            throw IOException("Faça login na WebView")
        }
        return document.select(popularMangaSelector()).map(::popularMangaFromElement).let {
            MangasPage(it, hasNextPage = false)
        }
    }

    // ============================ Latest ======================================

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()

    override fun latestUpdatesSelector() = throw UnsupportedOperationException()

    override fun latestUpdatesFromElement(element: Element) = throw UnsupportedOperationException()

    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException()

    // ============================ Search ======================================

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = fetchPopularManga(page).map { mangasPage ->
        MangasPage(mangasPage.mangas.filter { it.title.contains(query, ignoreCase = true) }, hasNextPage = false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = throw UnsupportedOperationException()

    override fun searchMangaSelector() = throw UnsupportedOperationException()

    override fun searchMangaFromElement(element: Element) = throw UnsupportedOperationException()

    override fun searchMangaNextPageSelector() = throw UnsupportedOperationException()

    // ============================ Details =====================================

    override fun getMangaUrl(manga: SManga) = "$baseUrl/projeto/${manga.getProjectId()}"

    override fun mangaDetailsRequest(manga: SManga): Request = GET(getMangaUrl(manga), headers)

    private fun SManga.getProjectId() = url.replace("/", "").substringAfter(ENTRY_URL_REGEX)

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        with(document) {
            title = selectFirst(".content h2")!!.text()
            thumbnail_url = selectFirst(".trailer-box img")?.absUrl("src")
            description = selectFirst(".content p")?.text()
            selectFirst("section[data-status]")?.attr("data-status")?.let {
                status = when (it.lowercase()) {
                    "ongoing" -> SManga.ONGOING
                    "completed" -> SManga.COMPLETED
                    "hiatus" -> SManga.ON_HIATUS
                    else -> SManga.UNKNOWN
                }
            }
            genre = select("h6:contains(Tags) + h6 > span").joinToString { it.text() }
        }
    }

    // ============================ Chapter =====================================

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> = super.chapterListParse(response).sortedByDescending(SChapter::chapter_number)

    override fun chapterListSelector() = ".manga-chapter"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        name = element.selectFirst("h5")!!.ownText()
        element.selectFirst("h6")?.let {
            date_upload = dateFormat.tryParse(it.text())
            SIMPLE_NUMBER_REGEX.find(name)?.groups?.get(0)?.value?.toFloat()?.let {
                chapter_number = it
            }
        }
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
    }

    // ============================ Pages =======================================

    override fun pageListParse(document: Document): List<Page> = document.select(".manga-page img").mapIndexed { index, element ->
        Page(index, imageUrl = element.absUrl("src"))
    }

    override fun imageUrlParse(document: Document) = ""

    // ============================== Utilities ==================================

    private fun String.substringAfter(regex: Regex): String = regex.find(this)?.value?.let(::substringAfter) ?: this

    companion object {
        private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT)
        private val POPULAR_REGEX = """projects(?:\s+)?=(?:\s+)?(.+\]);""".toRegex()
        private val SIMPLE_NUMBER_REGEX = """\d+(\.?\d+)?""".toRegex()
        private val ENTRY_URL_REGEX = """projetos?""".toRegex()
    }
}
