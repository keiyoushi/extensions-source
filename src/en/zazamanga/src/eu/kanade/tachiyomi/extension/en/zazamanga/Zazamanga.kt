package eu.kanade.tachiyomi.extension.en.zazamanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import okhttp3.Request
import org.jsoup.nodes.Element

class Zazamanga : Madara("Zazamanga", "https://www.zazamanga.com", "en") {
    override fun chapterListSelector() = "div.wp-manga-chapter"
    override fun searchMangaSelector() = "div.page-item-detail:not(.manga)"
    override val searchMangaUrlSelector = "p.widget-title a"

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
