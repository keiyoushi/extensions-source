package eu.kanade.tachiyomi.extension.es.mangalatino

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class MangaLatino : ParsedHttpSource() {

    override val name = "MangaLatino"

    override val baseUrl = "https://mangalatino.com"

    override val lang = "es"

    override val supportsLatest = true

    override val client: OkHttpClient = network.client.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 2)
        .build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Referer", baseUrl)

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/mangas?page=$page", headers)

    override fun popularMangaSelector(): String = "section.blog-listing div.row div.blog-grid"

    override fun popularMangaNextPageSelector(): String = "nav > ul.pagination > li > a[rel=next]"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.select("div.blog-info a").attr("href"))
        title = element.select("div.blog-info a").text()
        thumbnail_url = element.selectFirst("div.blog-img img")?.attr("abs:src")
    }

    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl, headers)

    override fun latestUpdatesSelector(): String = "div.row > div.col-sm-12:eq(1) ~ div.col-6:not(div.col-sm-12:gt(1) ~ div.col-6)"

    override fun latestUpdatesNextPageSelector(): String? = null

    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        val href = element.selectFirst("div.blog-info a")!!.attr("href")
        val slug = href.substringAfterLast("/").substringBeforeLast("-")
        url = "/serie/$slug"
        title = element.select("div.blog-info a").text().substringBeforeLast("Capítulo").trim()
        thumbnail_url = element.selectFirst("div.blog-img img")?.attr("abs:src")
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/mangas".toHttpUrl().newBuilder()
        if (query.isNotEmpty()) {
            url.addQueryParameter("buscar", query)
        } else {
            filters.forEach { filter ->
                when (filter) {
                    is GenreFilter -> {
                        url.addQueryParameter("tag", filter.toUriPart())
                    }
                    else -> {}
                }
            }
        }
        url.addQueryParameter("page", page.toString())
        return GET(url.build(), headers)
    }

    override fun searchMangaSelector(): String = popularMangaSelector()

    override fun searchMangaNextPageSelector(): String = popularMangaNextPageSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        with(document.selectFirst("div.starter-template")!!) {
            selectFirst("img[src]")?.let { thumbnail_url = it.attr("abs:src") }
            selectFirst("h1")?.let { title = it.text() }
            description = selectFirst("p")?.text()
            genre = select("> a.btn").joinToString { it.text() }
        }
    }

    override fun chapterListSelector(): String = "div.panel ul.list-group > li > a"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        name = element.selectFirst("span")!!.text()
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("section.blog-listing div.panel-body > img[src]").mapIndexed { i, element ->
            Page(i, "", element.attr("abs:src"))
        }
    }

    override fun imageRequest(page: Page): Request {
        val noRefererHeader = headers.newBuilder().removeAll("Referer").build()
        return GET(page.imageUrl!!, noRefererHeader)
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used!")

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("NOTA: La búsqueda por texto ignorará los demás filtros."),
        Filter.Separator(),
        GenreFilter(),
    )
}
