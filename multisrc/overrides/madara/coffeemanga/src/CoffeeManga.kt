package eu.kanade.tachiyomi.extension.en.coffeemanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import org.jsoup.nodes.Element

class CoffeeManga : Madara("Coffee Manga", "https://coffeemanga.io", "en") {
    override val useNewChapterEndpoint = false

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"

    override fun imageFromElement(element: Element): String? {
        return when {
            element.hasAttr("data-src") && element.attr("data-src").isNotEmpty() -> element.attr("abs:data-src")
            element.hasAttr("data-lazy-src") && element.attr("data-lazy-src").isNotEmpty() -> element.attr("abs:data-lazy-src")
            element.hasAttr("srcset") && element.attr("srcset").isNotEmpty() -> element.attr("abs:srcset").substringBefore(" ")
            else -> element.attr("abs:src")
        }
    }
}
