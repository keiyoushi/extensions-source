package eu.kanade.tachiyomi.extension.en.zazamanga

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.utils.asJsoup
import okhttp3.Request
import org.jsoup.nodes.Element

@Source
abstract class Zazamanga : MadaraNoAjax() {
    override fun nextPageSelector() = "ul.pagination > li:last-child:not(.disabled) > a[href]"

    // Past page 100 the site serves page 100 again
    override suspend fun archivePage(page: Int, order: String, path: String, query: String): MangasPage {
        val document = client.get(archiveUrlBuilder(page, order, path, query).build()).asJsoup()
        if (page > 1 && document.selectFirst("ul.pagination > li.active")?.text() != page.toString()) return MangasPage(emptyList(), false)
        return MangasPage(parseArchive(document), document.selectFirst(nextPageSelector()) != null)
    }

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
