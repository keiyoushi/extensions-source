package eu.kanade.tachiyomi.extension.en.kirascans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia

class KiraScans : MangaThemesia(
    "Kira Scans",
    "https://kirascans.com",
    "en",
) {
    override fun chapterListSelector(): String = "#chapterlist li:not(:has(svg))"
}
