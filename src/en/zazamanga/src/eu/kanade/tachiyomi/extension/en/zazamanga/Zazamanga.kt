package eu.kanade.tachiyomi.extension.en.zazamanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import okhttp3.Request
import org.jsoup.nodes.Element

class Zazamanga : Madara("Zazamanga", "https://www.zazamanga.com", "en") {
    override fun chapterListSelector() = "div.wp-manga-chapter"
    override fun searchMangaSelector() = "div.page-item-detail:not(.manga)"
    override val searchMangaUrlSelector = "p.widget-title a"
    override fun popularMangaNextPageSelector(): String? = ".pagination li:last-child:not(.disabled)"
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/$mangaSubString/${searchPage(page)}?orderby=views", headers)
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/$mangaSubString/${searchPage(page)}?orderby=latest", headers)

    override fun searchRequest(page: Int, query: String, filters: FilterList): Request {
        val request = super.searchRequest(page, query, filters)
        val url = request.url.toString().replace("m_orderby=", "orderby=")
        return GET(url, request.headers)
    }

    override fun imageRequest(page: Page): Request {
        return GET(page.imageUrl!!, headers)
    }

    override fun imageFromElement(element: Element): String? {
        return when {
            element.hasAttr("data-src") -> element.attr("data-src")
            element.hasAttr("data-lazy-src") -> element.attr("data-lazy-src")
            element.hasAttr("srcset") -> element.attr("srcset").getSrcSetImage()
            element.hasAttr("data-cfsrc") -> element.attr("data-cfsrc")
            else -> element.attr("src")
        }
    }
}
