package eu.kanade.tachiyomi.extension.en.mangaleveling

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SChapter
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class MangaLeveling : Madara("Manga Leveling", "https://mangaleveling.com", "en", SimpleDateFormat("MM/dd/yyyy", Locale.US)) {

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()

        with(element) {
            select(chapterUrlSelector).first()?.let { urlElement ->
                chapter.url = urlElement.attr("abs:href").let {
                    it.substringBefore("?style=paged") + if (!it.endsWith(chapterUrlSuffix)) chapterUrlSuffix else ""
                }
                chapter.name = urlElement.selectFirst("span")!!.text()
            }
            // Dates can be part of a "new" graphic or plain text
            // Added "title" alternative
            chapter.date_upload = select("img:not(.thumb)").firstOrNull()?.attr("alt")?.let { parseRelativeDate(it) }
                ?: select("span a").firstOrNull()?.attr("title")?.let { parseRelativeDate(it) }
                ?: parseChapterDate(select(chapterDateSelector()).firstOrNull()?.text())
        }

        return chapter
    }
}
