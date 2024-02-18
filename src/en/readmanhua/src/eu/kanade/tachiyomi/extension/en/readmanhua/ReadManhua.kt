package eu.kanade.tachiyomi.extension.en.readmanhua

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SChapter
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ReadManhua : Madara(
    "ReadManhua",
    "https://readmanhua.net",
    "en",
    dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.US),
) {

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        val year = Calendar.getInstance().get(Calendar.YEAR).toLong()

        with(element) {
            selectFirst(chapterUrlSelector)?.let { urlElement ->
                chapter.url = urlElement.attr("abs:href").let {
                    it.substringBefore("?style=paged") + if (!it.endsWith(chapterUrlSuffix)) chapterUrlSuffix else ""
                }
                chapter.name = urlElement.text()
            }

            // Dates can be part of a "new" graphic or plain text
            chapter.date_upload = selectFirst("img")?.attr("alt")?.let { parseRelativeDate(it) }
                ?: parseChapterDate(selectFirst("span.chapter-release-date i")?.text() + " " + year)
        }

        return chapter
    }
}
