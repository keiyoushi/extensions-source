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
            // Unfortunately Utoon doesn't include the year in the upload date.
            // As a workaround, assume it's from the current year, or last year
            // if the date is in the future.
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            val upload = element.selectFirst("span a")?.attr("title")?.let { parseRelativeDate(it) } ?: parseChapterDate("${element.selectFirst(chapterDateSelector())?.text()} $currentYear")
            val now = System.currentTimeMillis()
            date_upload = if (now < upload) {
                parseChapterDate("${element.selectFirst(chapterDateSelector())?.text()} ${currentYear - 1}")
            } else {
                upload
            }
        }
    }
}
