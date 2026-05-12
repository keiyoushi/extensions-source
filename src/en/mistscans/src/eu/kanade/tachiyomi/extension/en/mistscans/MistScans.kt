package eu.kanade.tachiyomi.extension.en.mistscans

import eu.kanade.tachiyomi.multisrc.keyoapp.Keyoapp
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Element

class MistScans : Keyoapp("Mist Scans", "https://mistscans.com", "en") {

    override fun popularMangaSelector() = ".series-splide .splide__slide"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        val a = element.selectFirst("a[href]")!!
        title = a.attr("title")
        setUrlWithoutDomain(a.attr("abs:href"))
        thumbnail_url = element.getImageUrl("*[style*=background-image]")
    }
}
