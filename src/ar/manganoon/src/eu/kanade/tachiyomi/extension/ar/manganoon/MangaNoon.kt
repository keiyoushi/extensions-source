package eu.kanade.tachiyomi.extension.ar.manganoon

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.SChapter
import org.jsoup.nodes.Element
import java.util.Calendar

class MangaNoon : MangaThemesia(
    "مانجا نون",
    "https://noonscan.site",
    "ar",
) {

    override fun chapterFromElement(element: Element): SChapter {
        return super.chapterFromElement(element).apply {
            date_upload = element.selectFirst(".chapterdate")?.text().parseChapterDate()
        }
    }

    // From Galaxy
    override fun String?.parseChapterDate(): Long {
        this ?: return 0L

        val number = Regex("""(\d+)""").find(this)?.value?.toIntOrNull() ?: 0
        val cal = Calendar.getInstance()

        return when {
            listOf("second", "ثانية").any { contains(it, true) } -> {
                cal.apply { add(Calendar.SECOND, -number) }.timeInMillis
            }

            contains("دقيقتين", true) -> {
                cal.apply { add(Calendar.MINUTE, -2) }.timeInMillis
            }
            listOf("minute", "دقائق").any { contains(it, true) } -> {
                cal.apply { add(Calendar.MINUTE, -number) }.timeInMillis
            }

            contains("ساعتان", true) -> {
                cal.apply { add(Calendar.HOUR, -2) }.timeInMillis
            }
            listOf("hour", "ساعات").any { contains(it, true) } -> {
                cal.apply { add(Calendar.HOUR, -number) }.timeInMillis
            }

            contains("يوم", true) -> {
                cal.apply { add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis
            }
            contains("يومين", true) -> {
                cal.apply { add(Calendar.DAY_OF_YEAR, -2) }.timeInMillis
            }
            listOf("day", "أيام").any { contains(it, true) } -> {
                cal.apply { add(Calendar.DAY_OF_YEAR, -number) }.timeInMillis
            }

            contains("أسبوع", true) -> {
                cal.apply { add(Calendar.WEEK_OF_YEAR, -1) }.timeInMillis
            }
            contains("أسبوعين", true) -> {
                cal.apply { add(Calendar.WEEK_OF_YEAR, -2) }.timeInMillis
            }
            listOf("week", "أسابيع").any { contains(it, true) } -> {
                cal.apply { add(Calendar.WEEK_OF_YEAR, -number) }.timeInMillis
            }

            contains("شهر", true) -> {
                cal.apply { add(Calendar.MONTH, -1) }.timeInMillis
            }
            contains("شهرين", true) -> {
                cal.apply { add(Calendar.MONTH, -2) }.timeInMillis
            }
            listOf("month", "أشهر").any { contains(it, true) } -> {
                cal.apply { add(Calendar.MONTH, -number) }.timeInMillis
            }

            contains("سنة", true) -> {
                cal.apply { add(Calendar.YEAR, -1) }.timeInMillis
            }
            contains("سنتان", true) -> {
                cal.apply { add(Calendar.YEAR, -2) }.timeInMillis
            }
            listOf("year", "سنوات").any { contains(it, true) } -> {
                cal.apply { add(Calendar.YEAR, -number) }.timeInMillis
            }

            else -> 0L
        }
    }
}
