package eu.kanade.tachiyomi.extension.en.mistscans

import eu.kanade.tachiyomi.multisrc.keyoapp.Keyoapp
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import org.jsoup.nodes.Element

@Source
abstract class MistScans : Keyoapp() {

    override fun popularMangaSelector() = ".series-splide .splide__slide"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        val a = element.selectFirst("a[href]")!!
        title = a.attr("title")
        setUrlWithoutDomain(a.attr("abs:href"))
        thumbnail_url = element.getImageUrl("*[style*=background-image]")
    }
}
