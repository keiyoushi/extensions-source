package eu.kanade.tachiyomi.extension.en.manhwalike

import eu.kanade.tachiyomi.extension.en.manhwalike.ManhwalikeHelper.buildApiHeaders
import eu.kanade.tachiyomi.extension.en.manhwalike.ManhwalikeHelper.toDate
import eu.kanade.tachiyomi.extension.en.manhwalike.ManhwalikeHelper.toFormRequestBody
import eu.kanade.tachiyomi.extension.en.manhwalike.ManhwalikeHelper.toOriginal
import eu.kanade.tachiyomi.extension.en.manhwalike.ManhwalikeHelper.toStatus
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element

class Manhwalike : HttpSource() {
    override val name = "Manhwalike"

    override val baseUrl = "https://manhwalike.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // popular
    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("ul.list-hot div.visual").map { element ->
            SManga.create().apply {
                element.selectFirst("h3.title a")?.text()?.also { title = it }
                element.selectFirst("a")?.absUrl("href")?.also { setUrlWithoutDomain(it) }
                thumbnail_url = element.selectFirst("img")?.toOriginal()
            }
        }
        return MangasPage(mangas, false)
    }

    // latest
    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("ul.slick_item div.visual").map { element ->
            SManga.create().apply {
                element.selectFirst("h3.title a")?.text()?.also { title = it }
                element.selectFirst("a")?.absUrl("href")?.also { setUrlWithoutDomain(it) }
                thumbnail_url = element.selectFirst("img")?.toOriginal()
            }
        }
        return MangasPage(mangas, false)
    }

    // search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = if (query.isNotEmpty()) {
        val requestBody = query.toFormRequestBody()
        val requestHeaders = headersBuilder().buildApiHeaders(requestBody)
        POST("$baseUrl/search/html/1", requestHeaders, requestBody)
    } else {
        val url = baseUrl.toHttpUrl().newBuilder()
        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> filter.toUriPart().also { url.addPathSegment(it) }
                else -> {}
            }
        }
        url.addQueryParameter("page", page.toString())
        GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = when {
            document.select("ul.normal li").isEmpty() -> document.select("ul li").map { element ->
                searchMangaFromElement(element)
            }
            else -> document.select("ul.normal li").map { element ->
                searchMangaFromElement(element)
            }
        }
        val hasNextPage = document.selectFirst("ul.pagination li:last-child a") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        element.selectFirst("img")?.attr("alt")?.also { title = it }
        element.selectFirst("img")?.toOriginal()?.also { thumbnail_url = it }
        element.selectFirst("a")?.absUrl("href")?.also { setUrlWithoutDomain(it) }
    }

    // details
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            author = document.selectFirst("div.author a")?.text()
            status = document.selectFirst("small:contains(Status) + strong")?.text().toStatus()
            genre = document.select("div.categories a").joinToString { it.text() }
            description = document.selectFirst("div.summary-block p.about")?.text()
            thumbnail_url = document.selectFirst("div.fixed-img img")?.absUrl("src")
        }
    }

    // chapters
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("ul.chapter-list li").map { element ->
            SChapter.create().apply {
                element.selectFirst("a")?.absUrl("href")?.also { setUrlWithoutDomain(it) }
                element.selectFirst("a")?.text()?.also { name = it }
                element.selectFirst(".time")?.text().toDate().also { date_upload = it }
            }
        }
    }

    // pages
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select(".chapter-content .page-chapter img").mapIndexed { i, img ->
            Page(i, imageUrl = img.absUrl("src"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // filters
    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: Ignored if using text search!"),
        Filter.Separator(),
        GenreFilter(),
    )
}
