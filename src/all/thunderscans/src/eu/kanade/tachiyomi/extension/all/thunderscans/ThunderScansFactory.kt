package eu.kanade.tachiyomi.extension.all.thunderscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesiaAlt
import eu.kanade.tachiyomi.source.SourceFactory
import java.text.SimpleDateFormat
import java.util.Locale

class ThunderScansFactory : SourceFactory {
    override fun createSources() = listOf(
        ThunderScansAR(),
        ThunderScansEN(),
    )
}

class ThunderScansAR : MangaThemesiaAlt(
    "Thunder Scans",
    "https://thunderscans.com",
    "ar",
    dateFormat = SimpleDateFormat("MMM d, yyy", Locale("ar")),
)

class ThunderScansEN : MangaThemesiaAlt(
    "Thunder Scans",
    "https://en-thunderepic.com",
    "en",
    mangaUrlDirectory = "/comics",
)
