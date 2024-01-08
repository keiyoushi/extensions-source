package eu.kanade.tachiyomi.extension.ar.areamanga

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.SManga
import java.text.SimpleDateFormat
import java.util.Locale

class AreaManga : MangaThemesia(
    "أريا مانجا",
    "https://www.areascans.net",
    "ar",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("ar")),
) {
    override val seriesArtistSelector =
        ".tsinfo .imptdt:contains(الرسام) i, ${super.seriesArtistSelector}"
    override val seriesAuthorSelector =
        ".tsinfo .imptdt:contains(المؤلف) i, ${super.seriesAuthorSelector}"
    override val seriesStatusSelector =
        ".tsinfo .imptdt:contains(الحالة) i, ${super.seriesStatusSelector}"
    override val seriesTypeSelector =
        ".tsinfo .imptdt:contains(النوع) i, ${super.seriesTypeSelector}"

    override fun String?.parseStatus() = when {
        this == null -> SManga.UNKNOWN
        this.contains("مستمر", ignoreCase = true) -> SManga.ONGOING
        this.contains("مكتمل", ignoreCase = true) -> SManga.COMPLETED
        this.contains("متوقف", ignoreCase = true) -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }
}
