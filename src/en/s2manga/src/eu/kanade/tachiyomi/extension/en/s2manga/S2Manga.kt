package eu.kanade.tachiyomi.extension.en.s2manga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import org.jsoup.nodes.Element

@Source
abstract class S2Manga : Madara() {

    override fun imageFromElement(element: Element): String? = super.imageFromElement(element)?.trim()
}
