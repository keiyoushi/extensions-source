package eu.kanade.tachiyomi.extension.ja.mangarawplus

import eu.kanade.tachiyomi.multisrc.madara.Madara
import org.jsoup.nodes.Element

class MangaRawPlus : Madara("MANGARAW+", "https://newmangaraw.net", "ja") {
    override val mangaSubString = "ts"
    override fun imageFromElement(element: Element): String? {
        return when {
            element.hasAttr("data-src-img") -> element.attr("abs:data-src-img")
            else -> element.attr("abs:src")
        }
    }
    override val useNewChapterEndpoint = false
}
