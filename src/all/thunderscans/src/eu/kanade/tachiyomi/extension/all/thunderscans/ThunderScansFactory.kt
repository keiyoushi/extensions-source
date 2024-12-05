package eu.kanade.tachiyomi.extension.all.thunderscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesiaAlt
import eu.kanade.tachiyomi.source.SourceFactory
import java.text.SimpleDateFormat
import java.util.Locale

class ThunderScansFactory : SourceFactory {
    override fun createSources() = listOf(
        LavaScans(),
        ThunderScans(),
    )
}

class LavaScans : MangaThemesiaAlt(
    "Lava Scans",
    "https://lavatoons.com",
    "ar",
    dateFormat = SimpleDateFormat("MMM d, yyy", Locale("ar")),
) {
    override val id = 3209001028102012989
}

class ThunderScans : MangaThemesiaAlt(
    "Thunder Scans",
    "https://en-thunderscans.com",
    "en",
    mangaUrlDirectory = "/comics",
)
