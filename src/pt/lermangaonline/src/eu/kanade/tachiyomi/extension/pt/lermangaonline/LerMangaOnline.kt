package eu.kanade.tachiyomi.extension.pt.lermangaonline

import eu.kanade.tachiyomi.extension.pt.lermangaonline.LerMangaOnlineFilters.GenresFilter
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class LerMangaOnline : ParsedHttpSource() {
    override val name = "Ler Mangá Online"

    override val baseUrl = "https://lermangaonline.com.br"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 1, 1, TimeUnit.SECONDS)
        .build()

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        name = element.selectFirst("div.capitulo")!!.ownText()
        date_upload = (element.selectFirst("span")?.text() ?: "").toDate()
        setUrlWithoutDomain(element.absUrl("href"))
    }

    override fun chapterListSelector() = "div.capitulos a"

    override fun imageUrlParse(document: Document) = ""

    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        title = element.selectFirst("section h3")!!.text()
        thumbnail_url = element.selectFirst("div.poster img")?.absUrl("src")
        setUrlWithoutDomain(element.selectFirst("div.poster a")!!.absUrl("href"))
    }

    override fun latestUpdatesNextPageSelector() = "div.wp-pagenavi [aria-current] + a"

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/capitulo/page/$page".toHttpUrl().newBuilder()
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesSelector() = "div.box-indx section.materias article"

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val info = document.selectFirst("div.box-flex")
        title = info!!.selectFirst("div.sinopse a")!!.text()
        description = info.selectFirst("div.sinopse div:nth-child(2)")?.text()
        thumbnail_url = info.selectFirst("div.poster img")?.srcAttr()
        genre = document.select("div.categorias-blog a").joinToString { it.text() }
        status = SManga.UNKNOWN
    }

    override fun pageListParse(document: Document): List<Page> {
        val elements = document.select("div.images img")
        return elements.mapIndexed { i, el ->
            Page(i, imageUrl = el.srcAttr())
        }
    }

    override fun popularMangaFromElement(element: Element) = latestUpdatesFromElement(element)

    override fun popularMangaNextPageSelector() = latestUpdatesNextPageSelector()

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/page/$page".toHttpUrl().newBuilder()
            .build()
        return GET(url, headers)
    }

    override fun popularMangaSelector() = latestUpdatesSelector()

    override fun searchMangaFromElement(element: Element) = latestUpdatesFromElement(element)

    override fun searchMangaNextPageSelector() = latestUpdatesNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filter = filters.first() as GenreFilter<*>
        val genre = filter.selected
        val url = "$baseUrl/${if (genre.isGlobal()) "" else genre.slug + "/"}page/$page".toHttpUrl().newBuilder()
            .addQueryParameter("s", query)
            .build()
        return GET(url, headers)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return if (query.startsWith(PREFIX_SLUG_SEARCH)) {
            val slug = query.removePrefix(PREFIX_SLUG_SEARCH)
            client.newCall(GET("$baseUrl/$slug", headers))
                .asObservableSuccess()
                .map(::searchMangaBySlugParse)
        } else {
            super.fetchSearchManga(page, query, filters)
        }
    }

    private fun searchMangaBySlugParse(response: Response): MangasPage =
        MangasPage(listOf(mangaDetailsParse(response.asJsoup())), false)


    override fun getFilterList(): FilterList = FilterList(GenresFilter)

    override fun searchMangaSelector() = latestUpdatesSelector()

    private fun Element.srcAttr(): String = when {
        hasAttr("data-src") -> absUrl("data-src")
        else -> absUrl("src")
    }

    private fun String.toDate(): Long {
        return runCatching { DATE_FORMATTER.parse(trim())?.time }
            .getOrNull() ?: 0L
    }

    companion object {
        const val PREFIX_SLUG_SEARCH = "slug:"
        private val DATE_FORMATTER by lazy {
            SimpleDateFormat("dd-MM-yyyy", Locale("pt", "BR"))
        }
    }
}
