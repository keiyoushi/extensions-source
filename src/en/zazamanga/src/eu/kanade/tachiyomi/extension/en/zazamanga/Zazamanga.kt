package eu.kanade.tachiyomi.extension.en.zazamanga

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.annotation.Source
import okhttp3.Request
import org.jsoup.nodes.Element

@Source
abstract class Zazamanga : MadaraNoAjax() {
    override fun chapterListSelector() = "div.wp-manga-chapter"

    override fun imageRequest(page: Page): Request = GET(page.imageUrl!!, headers)

    override fun imageFromElement(element: Element): String? = when {
        element.hasAttr("data-src") -> element.attr("data-src")
        element.hasAttr("data-lazy-src") -> element.attr("data-lazy-src")
        element.hasAttr("srcset") -> element.attr("srcset").getSrcSetImage()
        element.hasAttr("data-cfsrc") -> element.attr("data-cfsrc")
        else -> element.attr("src")
    }
}
