package eu.kanade.tachiyomi.extension.en.shojoscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.SManga

class ShojoScans : MangaThemesia(
    "Shojo Scans",
    "https://shojoscans.com",
    "en",
    mangaUrlDirectory = "/comics",
) {
    override fun String?.parseStatus() = when {
        this == null -> SManga.UNKNOWN
        this.contains("ongoing", ignoreCase = true) -> SManga.ONGOING
        this.contains("mass released", ignoreCase = true) -> SManga.ONGOING
        else -> SManga.UNKNOWN
    }
}
