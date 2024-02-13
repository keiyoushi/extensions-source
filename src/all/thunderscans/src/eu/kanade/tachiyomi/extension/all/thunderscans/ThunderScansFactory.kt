package eu.kanade.tachiyomi.extension.all.thunderscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.SourceFactory
import java.text.SimpleDateFormat
import java.util.Locale

class ThunderScansFactory : SourceFactory {
    override fun createSources() = listOf(
        ThunderScansAR(),
        ThunderScansEN(),
    )
}

class ThunderScansAR : MangaThemesia(
    "Thunder Scans",
    "https://thunderscans.com",
    "ar",
    dateFormat = SimpleDateFormat("MMM d, yyy", Locale("ar")),
)

class ThunderScansEN : MangaThemesia(
    "Thunder Scans",
    "https://en-thunderscans.com",
    "en",
    mangaUrlDirectory = "/comics",
)
