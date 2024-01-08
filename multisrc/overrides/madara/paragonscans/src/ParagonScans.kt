package eu.kanade.tachiyomi.extension.en.paragonscans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ParagonScans : Madara(
    "Paragon Scans",
    "https://paragonscans.com",
    "en",
    dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.ROOT),
) {
    override val useNewChapterEndpoint = true
    override val mangaSubString = "mangax"

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"

    override fun parseChapterDate(date: String?): Long {
        date ?: return 0

        val splitDate = date.split(' ')
        if (splitDate.size < 2) {
            return super.parseChapterDate(date)
        }

        val (amountStr, unit) = splitDate
        val amount = amountStr.toIntOrNull()
            ?: return super.parseChapterDate(date)

        val cal = Calendar.getInstance()
        return when (unit) {
            "s" -> cal.apply { add(Calendar.SECOND, -amount) }.timeInMillis // not observed
            "m" -> cal.apply { add(Calendar.MINUTE, -amount) }.timeInMillis // not observed
            "h" -> cal.apply { add(Calendar.HOUR_OF_DAY, -amount) }.timeInMillis
            "d" -> cal.apply { add(Calendar.DAY_OF_MONTH, -amount) }.timeInMillis
            else -> super.parseChapterDate(date)
        }
    }
}
