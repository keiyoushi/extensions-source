package eu.kanade.tachiyomi.extension.en.utoon

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SChapter
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class Utoon : Madara(
    "Utoon",
    "https://utoon.net",
    "en",
    SimpleDateFormat("dd MMM yyyy", Locale.US),
) {
    override val useNewChapterEndpoint = true

    override val useLoadMoreRequest = LoadMoreStrategy.Always

    override fun chapterListSelector() = "li.wp-manga-chapter:not(.premium-block)"

    override fun chapterFromElement(element: Element): SChapter {
        return super.chapterFromElement(element).apply {
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            date_upload = element.selectFirst("span a")?.attr("title")?.let { parseRelativeDate(it) }
                ?: parseChapterDate("${element.selectFirst(chapterDateSelector())?.text()} $currentYear")
        }
    }
}
