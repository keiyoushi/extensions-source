package eu.kanade.tachiyomi.extension.tr.majorscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.SManga
import java.text.SimpleDateFormat
import java.util.Locale

class MajorScans : MangaThemesia(
    "MajorScans",
    "https://www.majorscans.com",
    "tr",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("tr")),
) {
    override val seriesStatusSelector = ".imptdt:contains(Durumu) i"

    override val pageSelector = "div#readerarea img:not(noscript img)"

    override fun String?.parseStatus(): Int = when {
        this == null -> SManga.UNKNOWN
        listOf("devam ediyor", "güncel").any { this.contains(it, ignoreCase = true) } -> SManga.ONGOING
        this.contains("tamamlandı", ignoreCase = true) -> SManga.COMPLETED
        this.contains("bırakıldı", ignoreCase = true) -> SManga.CANCELLED
        this.contains("sezon finali", ignoreCase = true) -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }
}
