package eu.kanade.tachiyomi.extension.ja.rawlh

import eu.kanade.tachiyomi.multisrc.fmreader.FMReader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class WeLoveManga : FMReader("WeLoveManga", "https://weloma.art", "ja") {
    // Formerly "RawLH"
    override val id = 7595224096258102519

    override val chapterUrlSelector = ""
    override fun pageListParse(document: Document): List<Page> {
        fun Element.decoded(): String {
            return this.attr("data-src").trimEnd()
        }

        return document.select(pageListImageSelector).mapIndexed { i, img ->
            Page(i, document.location(), img.decoded())
        }
    }

    // Referer needs to be chapter URL
    override fun imageRequest(page: Page): Request = GET(page.imageUrl!!, headersBuilder().set("Referer", page.url).build())

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        element.select(headerSelector).let {
            setUrlWithoutDomain(it.attr("abs:href"))
            title = it.text()
        }
        thumbnail_url = element
            .select("div.content.img-in-ratio")
            .first()!!
            .attr("style")
            .let { BACKGROUND_IMAGE_REGEX.find(it)?.groups?.get(1)?.value }
    }

    companion object {
        val BACKGROUND_IMAGE_REGEX = Regex("""url\(['"]?(.*?)['"]?\)""")
    }
}
