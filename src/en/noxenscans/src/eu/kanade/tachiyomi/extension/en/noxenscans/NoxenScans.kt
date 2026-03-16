package eu.kanade.tachiyomi.extension.en.noxenscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia

class NoxenScans :
    MangaThemesia(
        "Noxen Scans",
        "https://noxenscan.com",
        "en",
    ) {
    override fun chapterListSelector(): String = "#chapterlist li:not(:has(svg))"
}
