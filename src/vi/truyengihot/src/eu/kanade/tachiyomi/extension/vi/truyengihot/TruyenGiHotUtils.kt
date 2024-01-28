package eu.kanade.tachiyomi.extension.vi.truyengihot

import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

object TruyenGiHotUtils {
    private val dateFormat: SimpleDateFormat by lazy {
        SimpleDateFormat("dd.M.yy", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
        }
    }

    internal fun parseChapterDate(date: String): Long {
        val trimmedDate = date.split(" ")

        if (trimmedDate.size < 2) {
            return runCatching {
                dateFormat.parse(date)!!.time
            }.getOrDefault(0L)
        }

        val calendar = Calendar.getInstance().apply {
            val amount = -trimmedDate[0].toInt()
            val field = when (trimmedDate[1]) {
                "giây" -> Calendar.SECOND
                "phút" -> Calendar.MINUTE
                "giờ" -> Calendar.HOUR_OF_DAY
                "ngày" -> Calendar.DAY_OF_MONTH
                "tuần" -> Calendar.WEEK_OF_MONTH
                "tháng" -> Calendar.MONTH
                "năm" -> Calendar.YEAR
                else -> Calendar.SECOND
            }

            add(field, amount)
        }

        return calendar.timeInMillis
    }

    internal fun parseThemes(element: Element): List<Genre> {
        return element.select("span[data-val]").map {
            Genre(it.text(), it.attr("data-val"))
        }
    }

    internal fun parseOptions(element: Element): List<Pair<String, String>> {
        return element.select("span[data-val]").map {
            Pair(it.text(), it.attr("data-val"))
        }
    }

    internal fun Element.imgAttr() = when {
        hasAttr("data-cfsrc") -> absUrl("data-cfsrc")
        hasAttr("data-lazy-src") -> absUrl("data-lazy-src")
        hasAttr("data-src") -> absUrl("data-src")
        hasAttr("srcset") -> attr("abs:srcset").substringBefore(" ")
        else -> absUrl("src")
    }

    internal fun Elements.textWithNewlines() = run {
        select("p, br").prepend("\\n")
        text().replace("\\n", "\n").replace("\n ", "\n")
    }
}
