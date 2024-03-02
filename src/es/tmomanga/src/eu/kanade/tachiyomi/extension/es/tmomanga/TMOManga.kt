package eu.kanade.tachiyomi.extension.es.tmomanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

class TMOManga : ParsedHttpSource() {

    override val baseUrl = "https://tmomanga.com"

    override val lang = "es"

    override val name = "TMO Manga"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/recomendados?page=$page", headers)

    override fun popularMangaSelector() = "div.page-content-listing div.page-item-detail"

    override fun popularMangaNextPageSelector() = "nav.navigation ul.pagination a[rel=next]"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        title = element.select(".manga-title-updated").text()
        thumbnail_url = element.selectFirst("img")?.imgAttr()
        setUrlWithoutDomain(element.select("div.manga_biblioteca > a").attr("href"))
    }

    override fun latestUpdatesRequest(page: Int) = GET(baseUrl, headers)

    private fun latestUpdatesWrapperSelector() = "div.site-content div.main-col div.main-col-inner"

    override fun latestUpdatesSelector() = "div.page-content-listing div.episode_thumb"

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.selectFirst(latestUpdatesWrapperSelector())!!
            .select(latestUpdatesSelector())
            .map { latestUpdatesFromElement(it) }

        return MangasPage(mangas, false)
    }

    override fun latestUpdatesNextPageSelector(): String? = null

    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        title = element.select(".manga-title-updated").text()
        thumbnail_url = element.selectFirst("img")?.imgAttr()
        val chapterUrl = element.selectFirst("a")!!.attr("href")
        setUrlWithoutDomain(chapterUrl.substringBeforeLast("-").replace("/capitulo/", "/manga/"))
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val urlBuilder = baseUrl.toHttpUrl().newBuilder()

        if (query.isNotBlank()) {
            urlBuilder.addPathSegment("biblioteca")
            urlBuilder.addQueryParameter("search", query)
        } else {
            urlBuilder.addPathSegment("genero")
            filters.forEach { filter ->
                when (filter) {
                    is GenreFilter -> urlBuilder.addPathSegment(filter.toUriPart())
                    else -> {}
                }
            }
        }

        urlBuilder.addQueryParameter("page", page.toString())
        return GET(urlBuilder.build(), headers)
    }

    override fun getFilterList(): FilterList {
        return FilterList(
            Filter.Header("Los filtros seran ignorados si se realiza una busqueda por texto"),
            Filter.Separator(),
            GenreFilter(),
        )
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.select("post-title > h1").text()
        genre = document.select("div.summary_content a.tags_manga").joinToString { it.ownText() }
        description = document.select("div.description-summary p").text()
        thumbnail_url = document.select("div.summary_image img").imgAttr()
    }

    override fun chapterListSelector() = "div.listing-chapters_wrap li.wp-manga-chapter"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        name = element.select("a").text()
        setUrlWithoutDomain(element.select("a").attr("href"))
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div#images_chapter img").mapIndexed { i, img ->
            Page(i, imageUrl = img.imgAttr())
        }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    private fun Element.imgAttr(): String {
        return when {
            hasAttr("data-src") -> attr("abs:data-src")
            hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
            else -> attr("abs:src")
        }
    }

    private fun Elements.imgAttr() = this.first()!!.imgAttr()
}
