package eu.kanade.tachiyomi.extension.en.mangagg

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.multisrc.madara.MadaraBase.ChapterMode
import keiyoushi.annotation.Source
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class MangaGG : Madara() {
    override val chapterMode = ChapterMode.MangaAjax
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.US)

    override fun imageFromElement(element: Element): String? {
        val url = element.attr("data-src").trim().ifEmpty {
            element.attr("data-lazy-src").trim()
        }.ifEmpty {
            element.attr("data-cfsrc").trim()
        }.ifEmpty {
            element.attr("data-manga-src").trim()
        }.ifEmpty {
            element.attr("src").trim()
        }

        // Jsoup's absUrl fails if a URL has leading spaces.
        // If it starts with http, it is already an absolute URL.
        return if (url.startsWith("http")) url else super.imageFromElement(element)?.trim()
    }
}
