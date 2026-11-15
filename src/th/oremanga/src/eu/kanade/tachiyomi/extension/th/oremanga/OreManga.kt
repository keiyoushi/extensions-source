package eu.kanade.tachiyomi.extension.th.oremanga

import eu.kanade.tachiyomi.multisrc.zmanga.ZManga
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.annotation.Source
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class OreManga : ZManga() {

    override val searchPath = "advance-search"

    override val typeFilterValues = arrayOf(
        Pair("All", ""),
        Pair("Manga", "Manga"),
        Pair("Manhua", "Manhua"),
        Pair("Manhwa", "Manhwa"),
        Pair("One-shot", "One-shot"),
        Pair("Doujinshi", "Doujinshi"),
    )

    override val dateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)

    private val thaiMonths = listOf(
        "มกราคม" to "January",
        "กุมภาพันธ์" to "February",
        "มีนาคม" to "March",
        "เมษายน" to "April",
        "พฤษภาคม" to "May",
        "มิถุนายน" to "June",
        "กรกฎาคม" to "July",
        "สิงหาคม" to "August",
        "กันยายน" to "September",
        "ตุลาคม" to "October",
        "พฤศจิกายน" to "November",
        "ธันวาคม" to "December",
    )

    override fun parseDate(dateString: String): Long {
        val englishDate = thaiMonths.fold(dateString) { date, (thai, english) ->
            date.replace(thai, english)
        }
        return super.parseDate(englishDate)
    }

    override fun pageListParse(document: Document): List<Page> = document.select(".reader-area-main img, .reader-area-main canvas").mapIndexed { index, element ->
        val imageUrl = if (element.tagName() == "canvas") {
            element.absUrl("data-url")
        } else {
            element.absUrl("src")
        }
        Page(index, imageUrl = imageUrl)
    }
}
