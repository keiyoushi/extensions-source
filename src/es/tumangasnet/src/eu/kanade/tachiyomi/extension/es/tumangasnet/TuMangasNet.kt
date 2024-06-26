package eu.kanade.tachiyomi.extension.es.tumangasnet

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class TuMangasNet : ParsedHttpSource() {

    override val name = "TuMangas.net"

    override val baseUrl = "https://tumangas.net"

    override val lang = "es"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 3, 1)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/biblioteca-manga?page=$page", headers)

    override fun popularMangaNextPageSelector() = searchMangaNextPageSelector()

    override fun popularMangaSelector() = searchMangaSelector()

    override fun popularMangaFromElement(element: Element) = searchMangaFromElement(element)

    override fun latestUpdatesRequest(page: Int) = GET(baseUrl, headers)

    override fun latestUpdatesNextPageSelector() = null

    override fun latestUpdatesSelector() = "ul.episodes article.episode"

    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(
            element.selectFirst("a")!!.attr("href")
                .substringBeforeLast("-")
                .replace("/leer-manga/", "/manga/"),
        )
        title = element.selectFirst(".title")!!.text().substringBeforeLast("Ep.").trim()
        thumbnail_url = element.selectFirst("figure > img")?.attr("src")
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = "$baseUrl/biblioteca-manga".toHttpUrl().newBuilder()
                .addQueryParameter("buscar", query)
                .addQueryParameter("page", page.toString())
                .build()

            return GET(url, headers)
        }

        val url = "$baseUrl/tag".toHttpUrl().newBuilder()
        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> {
                    url.addPathSegment(filter.toUriPart())
                }
                else -> {}
            }
        }

        url.addQueryParameter("page", page.toString())

        return GET(url.build(), headers)
    }

    override fun searchMangaNextPageSelector() = "ul.pagination li.page-item a[rel=next]"

    override fun searchMangaSelector() = "ul.animes article.anime"

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
        title = element.selectFirst(".title")!!.text()
        thumbnail_url = element.selectFirst("figure > img")?.attr("src")
    }

    override fun getFilterList() = FilterList(
        Filter.Header("NOTA: Los filtros no funcionan en la búsqueda por texto."),
        GenreFilter(),
    )

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        document.selectFirst("article.anime-single")!!.let { element ->
            title = element.selectFirst(".title")!!.text()
            genre = element.select("p.genres > span").joinToString { it.text() }
            description = element.selectFirst(".sinopsis")?.text()
            thumbnail_url = element.selectFirst("div.thumb figure > img")?.attr("abs:src")
        }
    }

    override fun chapterListSelector() = "ul.episodes-list > li"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("abs:href"))
        name = element.selectFirst("a > span")!!.text()
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div#chapter_imgs img[src]").mapIndexed { i, img ->
            Page(i, imageUrl = img.attr("abs:src"))
        }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()
}
