package eu.kanade.tachiyomi.extension.ja.manga1000

import eu.kanade.tachiyomi.multisrc.fmreader.FMReader
import eu.kanade.tachiyomi.source.model.SChapter
import org.jsoup.nodes.Element
import java.util.Calendar

class Manga1000 : FMReader("Manga1000", "https://manga1000.top", "ja") {
    override fun chapterFromElement(element: Element, mangaTitle: String): SChapter {
        return SChapter.create().apply {
            element.let {
                setUrlWithoutDomain(it.attr("abs:href"))
                name = it.attr("title")
            }

            date_upload = element.select(chapterTimeSelector)
                .let { if (it.hasText()) parseChapterDate(it.text()) else 0 }
        }
    }

    private fun parseChapterDate(date: String): Long {
        val value = date.split(' ')[dateValueIndex].toInt()
        val chapterDate = Calendar.getInstance().apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        when (date.split(' ')[dateWordIndex]) {
            "mins", "minutes" -> chapterDate.add(Calendar.MINUTE, value * -1)
            "hours" -> chapterDate.add(Calendar.HOUR_OF_DAY, value * -1)
            "days" -> chapterDate.add(Calendar.DATE, value * -1)
            "weeks" -> chapterDate.add(Calendar.DATE, value * 7 * -1)
            "months" -> chapterDate.add(Calendar.MONTH, value * -1)
            "years" -> chapterDate.add(Calendar.YEAR, value * -1)
            else -> return 0
        }

        return chapterDate.timeInMillis
    }
}
