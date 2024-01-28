package eu.kanade.tachiyomi.extension.ar.mangastorm

import eu.kanade.tachiyomi.lib.randomua.UserAgentType
import eu.kanade.tachiyomi.lib.randomua.setRandomUserAgent
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.lang.UnsupportedOperationException

class MangaStorm : ParsedHttpSource() {

    override val name = "MangaStorm"

    override val lang = "ar"

    override val baseUrl = "https://mangastorm.org"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .setRandomUserAgent(
            UserAgentType.DESKTOP,
            filterInclude = listOf("chrome"),
        )
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/mangas?page=$page", headers)
    }

    override fun popularMangaSelector() = "div.row div.col"
    override fun popularMangaNextPageSelector() = ".page-link[rel=next]"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
        title = element.select(".manga-ct-title").text()
        thumbnail_url = element.selectFirst("img")?.imgAttr()
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET(baseUrl, headers)
    }

    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun latestUpdatesNextPageSelector() = null
    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/mangas".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("query", query.trim())
            .build()

        return GET(url, headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun mangaDetailsParse(document: Document): SManga {
        val root = document.selectFirst(".card-body .col-lg-9")!!

        return SManga.create().apply {
            title = document.select(".card-header").text()
            thumbnail_url = document.selectFirst("img.card-img-right")?.imgAttr()
            genre = root.select(".flex-wrap a").eachText().joinToString()
            description = root.selectFirst(".card-text")?.text()
        }
    }

    override fun chapterListSelector() = ".card-body a.btn-fixed-width"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.absUrl("href"))
        name = element.text()
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.text-center .img-fluid")
            .mapIndexed { idx, img ->
                Page(idx, "", img.imgAttr())
            }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    private fun Element.imgAttr() = when {
        hasAttr("data-cfsrc") -> attr("abs:data-cfsrc")
        hasAttr("data-src") -> attr("abs:data-src")
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("srcset") -> attr("abs:srcset").substringBefore(" ")
        else -> attr("abs:src")
    }
}
