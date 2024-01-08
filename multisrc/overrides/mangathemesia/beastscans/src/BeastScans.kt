package eu.kanade.tachiyomi.extension.ar.beastscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.SManga
import java.text.SimpleDateFormat
import java.util.Locale

class BeastScans : MangaThemesia(
    "Beast Scans",
    "https://beastscans.net",
    "ar",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("ar")),
) {
    override val seriesArtistSelector =
        ".infox .fmed:contains(الرسام) span, ${super.seriesArtistSelector}"
    override val seriesAuthorSelector =
        ".infox .fmed:contains(المؤلف) span, ${super.seriesAuthorSelector}"
    override val seriesStatusSelector =
        ".tsinfo .imptdt:contains(الحالة) i, ${super.seriesStatusSelector}"
    override val seriesTypeSelector =
        ".tsinfo .imptdt:contains(النوع) i, ${super.seriesTypeSelector}"

    override fun String?.parseStatus() = when {
        this == null -> SManga.UNKNOWN
        listOf("مستمر", "ongoing", "publishing").any { this.contains(it, ignoreCase = true) } -> SManga.ONGOING
        listOf("متوقف", "hiatus").any { this.contains(it, ignoreCase = true) } -> SManga.ON_HIATUS
        listOf("مكتمل", "completed").any { this.contains(it, ignoreCase = true) } -> SManga.COMPLETED
        listOf("dropped", "cancelled").any { this.contains(it, ignoreCase = true) } -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }
}
