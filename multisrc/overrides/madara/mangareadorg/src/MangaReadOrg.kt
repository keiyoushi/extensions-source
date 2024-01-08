package eu.kanade.tachiyomi.extension.en.mangareadorg

import eu.kanade.tachiyomi.multisrc.madara.Madara
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class MangaReadOrg : Madara(
    "MangaRead.org",
    "https://www.mangaread.org",
    "en",
    SimpleDateFormat("dd.MM.yyy", Locale.US),
) {
    override fun imageFromElement(element: Element): String? {
        return when {
            element.hasAttr("data-cfsrc") -> element.attr("abs:data-cfsrc")
            element.hasAttr("data-src") -> element.attr("abs:data-src")
            element.hasAttr("data-lazy-src") -> element.attr("abs:data-lazy-src")
            element.hasAttr("srcset") -> element.attr("abs:srcset").substringBefore(" ")
            else -> element.attr("abs:src")
        }
    }
}
