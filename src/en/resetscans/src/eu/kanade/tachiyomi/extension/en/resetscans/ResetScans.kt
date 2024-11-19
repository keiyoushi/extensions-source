package eu.kanade.tachiyomi.extension.en.resetscans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SChapter
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ResetScans : Madara(
    "Reset Scans",
    "https://rspro.xyz",
    "en",
    dateFormat = SimpleDateFormat("MMM dd yyyy", Locale.US),
) {
    // Moved from FuzzyDoodle to Madara
    override val versionId = 3

    override val useNewChapterEndpoint = true

    override fun chapterListSelector() = "li.wp-manga-chapter>div:not(:has(a[href*=#]))"

    override fun chapterFromElement(element: Element): SChapter {
        // Year is not defined so just use the current one
        return super.chapterFromElement(element).apply {
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            date_upload = parseChapterDate("${element.selectFirst(chapterDateSelector())?.text()} $currentYear")
        }
    }
}
