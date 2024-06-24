package eu.kanade.tachiyomi.extension.ja.mangarawplus

import eu.kanade.tachiyomi.multisrc.madara.Madara
import org.jsoup.nodes.Element

class MangaRawPlus : Madara("MANGARAW+", "https://mangarawpedia.com", "ja") {
    override fun imageFromElement(element: Element): String? {
        return when {
            element.hasAttr("data-src-img") -> element.absUrl("data-src-img")
            else -> super.imageFromElement(element)
        }
    }
    override val useNewChapterEndpoint = false
}
