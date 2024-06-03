package eu.kanade.tachiyomi.extension.ja.mangarawplus

import eu.kanade.tachiyomi.multisrc.madara.Madara
import org.jsoup.nodes.Element

class MangaRawPlus : Madara("MANGARAW+", "https://newmangaraw.net", "ja") {
    override val mangaSubString = "ts"
    override fun imageFromElement(element: Element): String? {
        return when {
            element.hasAttr("data-src-img") -> element.attr("abs:data-src-img")
            element.hasAttr("data-src") -> element.attr("abs:data-src")
            element.hasAttr("data-lazy-src") -> element.attr("abs:data-lazy-src")
            element.hasAttr("srcset") -> element.attr("abs:srcset").substringBefore(" ")
            element.hasAttr("data-cfsrc") -> element.attr("abs:data-cfsrc")
            else -> element.attr("abs:src")
        }
    }
    override val useNewChapterEndpoint = false
}
