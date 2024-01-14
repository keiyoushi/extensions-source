package eu.kanade.tachiyomi.extension.pt.portugamanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
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
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class PortugaManga : ParsedHttpSource() {

    override val name = "Portuga Manga"

    override val baseUrl = "https://portugamanga.online"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 1, 2, TimeUnit.SECONDS)
        .build()

    override fun popularMangaRequest(page: Int): Request {
        return GET(baseUrl, headers)
    }

    override fun popularMangaSelector(): String = "div#maisLidos > div.itemmanga"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("h3")?.text() ?: "Title not found"
        thumbnail_url = element.selectFirst("img")!!.srcAttr()
        setUrlWithoutDomain(element.attr("href"))
    }

    override fun popularMangaNextPageSelector(): String? = null

    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl, headers)

    override fun latestUpdatesSelector() = "div.atualizacao"

    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("a > h3")?.text() ?: "Title not found"
        thumbnail_url = element.selectFirst("div > img")!!.absUrl("src")
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
    }

    override fun latestUpdatesNextPageSelector(): String? = "ul.pagination [aria-label=Next]"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        throw UnsupportedOperationException("Not implemented")

    override fun searchMangaParse(response: Response): MangasPage =
        throw UnsupportedOperationException("Not implemented")

    override fun searchMangaSelector() =
        throw UnsupportedOperationException("Not implemented")

    override fun searchMangaFromElement(element: Element) =
        throw UnsupportedOperationException("Not implemented")

    override fun searchMangaNextPageSelector() =
        throw UnsupportedOperationException("Not implemented")

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            title = document.selectFirst("h1")?.text() ?: "Untitled"

            description = document.selectFirst("#manga_capitulo_descricao")?.text()

            genre = document.select("h5.cg_color > a.label.label-warning")
                .map { it?.text() ?: "" }
                .filter { it.isNotBlank() }
                .joinToString(", ")

            val pageStatus = document.selectFirst("h5.cg_color > a.label.label-success")?.text()
                ?: ""

            status = when (pageStatus) {
                PAGE_STATUS_ONGOING -> SManga.ONGOING
                PAGE_STATUS_COMPLETED -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }

    override fun chapterListSelector() = "ul#capitulos > li"

    private fun dateUploadParse(content: String): Long {
        val date = content.replace("[()]".toRegex(), "")
        return if (date.isNotBlank()) { date.toDate() } else { 0L }
    }

    private fun chapterNameParse(content: String): String {
        return content.replace(DATE_PATTERN, "")
    }

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        name = chapterNameParse(element.selectFirst("a > div")?.ownText() ?: "Unnamed")
        date_upload = dateUploadParse(element.selectFirst("a > div span")?.ownText() ?: "")
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
    }

    override fun pageListParse(document: Document): List<Page> {
        val elements = document.select("div#capitulos_images img")
        return elements.mapIndexed { i, el ->
            Page(el?.attr("pag")?.toInt() ?: i, document.location(), el.srcAttr())
        }
    }

    override fun imageUrlParse(document: Document) = ""

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Referer", page.url)
            .build()
        return GET(page.imageUrl!!, newHeaders)
    }

    private fun Element.srcAttr(): String = when {
        hasAttr("data-src") -> absUrl("data-src")
        else -> absUrl("src")
    }

    private fun String.toDate(): Long {
        return runCatching { DATE_FORMATTER.parse(trim())?.time }
            .getOrNull() ?: 0L
    }

    companion object {
        const val PAGE_STATUS_ONGOING = "Ativo"
        const val PAGE_STATUS_COMPLETED = "Completo"
        private val DATE_PATTERN = "(<?date>\\(\\d+/\\d+/\\d+\\))".toRegex()

        private val DATE_FORMATTER by lazy {
            SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
        }
    }
}
