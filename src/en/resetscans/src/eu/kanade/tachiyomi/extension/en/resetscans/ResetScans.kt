package eu.kanade.tachiyomi.extension.en.resetscans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SChapter
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ResetScans :
    Madara(
        "Reset Scans",
        "https://reset-scans.org",
        "en",
        dateFormat = SimpleDateFormat("dd-MMM", Locale.US),
    ) {
    // Moved from FuzzyDoodle to Madara
    override val versionId = 3

    override val useNewChapterEndpoint = true

    override fun chapterListSelector() = "li.wp-manga-chapter:not(:has(a[href*='#']))"

    override fun searchMangaSelector() = ".rs-manga-library__card"
    override val searchMangaUrlSelector = ".rs-manga-library__card-title a"

    override fun popularMangaSelector() = searchMangaSelector()
    override val popularMangaUrlSelector = searchMangaUrlSelector

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = super.chapterListParse(response)

        var currentYear = Calendar.getInstance().get(Calendar.YEAR)
        var previousMonth = -1

        for (chapter in chapters) {
            if (chapter.date_upload > 0L) {
                val cal = Calendar.getInstance()
                cal.timeInMillis = chapter.date_upload

                // 1970 means the date was parsed without a year
                if (cal.get(Calendar.YEAR) == 1970) {
                    val month = cal.get(Calendar.MONTH)

                    if (previousMonth != -1) {
                        // Month jumping forward (e.g. Jan to Dec) means we crossed into the previous year
                        if (month - previousMonth >= 6) {
                            currentYear--
                        }
                    } else {
                        // If the first parsed date is in the future (+7 day buffer), it belongs to last year
                        cal.set(Calendar.YEAR, currentYear)
                        if (cal.timeInMillis > System.currentTimeMillis() + 604_800_000L) {
                            currentYear--
                        }
                    }

                    cal.set(Calendar.YEAR, currentYear)
                    chapter.date_upload = cal.timeInMillis

                    previousMonth = month
                } else {
                    // Update tracking variables using dates that already have a valid year
                    currentYear = cal.get(Calendar.YEAR)
                    previousMonth = cal.get(Calendar.MONTH)
                }
            }
        }

        return chapters
    }
}
