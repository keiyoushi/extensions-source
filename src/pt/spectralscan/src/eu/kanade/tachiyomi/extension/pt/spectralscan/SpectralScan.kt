package eu.kanade.tachiyomi.extension.pt.spectralscan

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException

class SpectralScan : ParsedHttpSource() {

    override val lang = "pt-BR"

    override val name = "Spectral Scan"

    override val baseUrl = "https://www.spectral.wtf"

    override val supportsLatest = true

    override val versionId = 3

    override val client = super.client.newBuilder()
        .rateLimit(2)
        .addInterceptor { chain ->
            chain.proceed(chain.request()).also {
                if (it.request.url.toString().contains("login")) {
                    it.close()
                    throw IOException("Faça o login na WebView para acessar o contéudo")
                }
            }
        }
        .build()

    // ==================== Popular ==========================

    private val popularFilter = FilterList(SelectFilter(vals = arrayOf("" to "popular"), parameter = "sort"))

    override fun popularMangaRequest(page: Int) = searchMangaRequest(page, "", popularFilter)
    override fun popularMangaSelector() = searchMangaSelector()
    override fun popularMangaFromElement(element: Element) = searchMangaFromElement(element)
    override fun popularMangaNextPageSelector() = searchMangaNextPageSelector()

    // ==================== Latest ==========================

    private val latestFilter = FilterList(SelectFilter(vals = arrayOf("" to "latest"), parameter = "sort"))

    override fun latestUpdatesRequest(page: Int) = searchMangaRequest(page, "", latestFilter)
    override fun latestUpdatesSelector() = searchMangaSelector()
    override fun latestUpdatesFromElement(element: Element) = searchMangaFromElement(element)
    override fun latestUpdatesNextPageSelector() = searchMangaNextPageSelector()

    // ==================== Search ==========================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/mangas".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("q", query)
        filters.forEach { filter ->
            when (filter) {
                is SelectFilter -> {
                    url.addQueryParameter(filter.parameter, filter.selected())
                }
                else -> {}
            }
        }
        return GET(url.build(), headers)
    }

    override fun searchMangaSelector() = ".content-grid .content-card"

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        title = element.selectFirst(".p-3 .text-sm")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
        setUrlWithoutDomain(element.absUrl("href"))
    }

    override fun searchMangaNextPageSelector() = "a[title='Última Página']"

    // ==================== Details =======================

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1")!!.text()
        thumbnail_url = document.selectFirst("img.item-cover-image")?.absUrl("src")
        description = document.selectFirst(".item-description")?.text()
        genre = document.select(".item-genres a").joinToString { it.text() }
        document.select("span:contains(Status) + span")?.text()?.let {
            status = when (it.lowercase()) {
                "em andamento" -> SManga.ONGOING
                else -> SManga.UNKNOWN
            }
        }
        setUrlWithoutDomain(document.location())
    }

    // ==================== Chapter =======================

    override fun chapterListSelector() = "a.chapter-item"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        name = element.selectFirst(".chapter-number")!!.text()
        scanlator = element.selectFirst(".chapter-meta span")?.text()
        setUrlWithoutDomain(element.absUrl("data-final-url"))
    }

    // ==================== Page ==========================

    override fun pageListParse(document: Document): List<Page> {
        return document.select(".manga-page-container").mapIndexed { index, element ->
            Page(index, imageUrl = "${element.absUrl("data-image-src")}")
        }
    }

    override fun imageUrlParse(document: Document) = ""

    // ==================== Filters ==========================

    override fun getFilterList(): FilterList {
        return FilterList(
            SelectFilter("Ordenar Por", "sort", sortList),
            SelectFilter("Gênero", "genre", genreList),
        )
    }
}
