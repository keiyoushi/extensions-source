package eu.kanade.tachiyomi.extension.pt.exhentainetbr

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class ExHentaiNetBR : ParsedHttpSource() {

    override val name = "ExHentai.net.br"

    override val baseUrl = "https://exhentai.net.br"

    override val lang = "pt-BR"

    override val supportsLatest = false

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/lista-de-mangas/page/$page")

    override fun popularMangaSelector() = "article.itemP"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        title = element.selectFirst("h3")!!.ownText()
        thumbnail_url = element.selectFirst("img")?.imgAttr()
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
    }

    override fun popularMangaNextPageSelector() = ".content-pagination li[class='active'] + li:not([class='next'])"

    override fun latestUpdatesFromElement(element: Element) =
        throw UnsupportedOperationException()

    override fun latestUpdatesNextPageSelector() =
        throw UnsupportedOperationException()

    override fun latestUpdatesRequest(page: Int) =
        throw UnsupportedOperationException()

    override fun latestUpdatesSelector() =
        throw UnsupportedOperationException()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        var url = "$baseUrl/page/$page".toHttpUrl().newBuilder()
            .addQueryParameter("s", query)
            .build()

        val filter = filters.filterIsInstance<AlphabetFilter>().first()

        if (query.isBlank() && filter.selected() != DEFAULT_FILTER_VALUE) {
            url = "$baseUrl/lista-de-mangas".toHttpUrl().newBuilder()
                .addQueryParameter("letra", filter.selected())
                .build()
        }
        return GET(url, headers)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith(PREFIX_SEARCH).not()) {
            return super.fetchSearchManga(page, query, filters)
        }

        val slug = query.substringAfter(PREFIX_SEARCH)
        return client.newCall(GET("$baseUrl/manga/$slug", headers))
            .asObservableSuccess()
            .map { MangasPage(listOf(mangaDetailsParse(it)), false) }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val url = response.request.url
        return if (url.queryParameter("letra") != null) {
            popularMangaParse(response)
        } else {
            super.searchMangaParse(response)
        }
    }

    override fun searchMangaSelector() = ".post ${popularMangaSelector()}"

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst(".stats_box h3")!!.text()
        description = document.selectFirst(".sinopse_manga .info_p:last-child")?.text()
        thumbnail_url = document.selectFirst(".anime_cover img")?.imgAttr()
        artist = document.selectFirst(".sinopse_manga h5:contains(Artista) + span")?.text()
        author = artist
        genre = document.select(".tag-btn").joinToString { it.ownText() }
        val statusLabel = document.selectFirst(".stats_box span")?.ownText() ?: ""
        status = when {
            statusLabel.equals("Completo", ignoreCase = true) -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        setUrlWithoutDomain(document.location())
    }

    override fun chapterListSelector() = ".chapter_content a"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        name = element.selectFirst(".name_chapter")!!.text()
        val date = element.selectFirst("span.release-date")?.ownText() ?: ""
        date_upload = date.substringAfter(":").trim().toDate()
        setUrlWithoutDomain(element.absUrl("href"))
    }

    override fun chapterListParse(response: Response) =
        super.chapterListParse(response).reversed()

    override fun pageListParse(document: Document) =
        document.select("div.manga_image > img").mapIndexed { index, element ->
            Page(index, imageUrl = element.imgAttr())
        }

    override fun imageUrlParse(document: Document) = ""

    override fun getFilterList(): FilterList {
        val alphabet = mutableListOf(DEFAULT_FILTER_VALUE).also {
            it += ('A'..'Z').map { "$it" }
        }

        return FilterList(
            Filter.Header(
                """
                    Busca por título possue prioridade.
                    Deixe em branco para pesquisar por letra
                """.trimIndent(),
            ),
            AlphabetFilter("Alfabeto", alphabet),
        )
    }

    private fun Element.imgAttr(): String {
        return when {
            hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
            hasAttr("data-src") -> attr("abs:data-src")
            hasAttr("data-cfsrc") -> attr("abs:data-cfsrc")
            else -> attr("abs:src")
        }
    }

    private fun String.toDate(): Long =
        try { dateFormat.parse(trim())!!.time } catch (_: Exception) { 0L }

    companion object {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT)
        const val PREFIX_SEARCH = "id:"
        const val DEFAULT_FILTER_VALUE = "Padrão"
    }

    class AlphabetFilter(displayName: String, private val vals: List<String>, state: Int = 0) :
        Filter.Select<String>(displayName, vals.toTypedArray(), state) {
        fun selected() = vals[state]
    }
}
