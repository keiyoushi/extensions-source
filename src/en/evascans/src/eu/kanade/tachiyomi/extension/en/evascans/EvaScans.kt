package eu.kanade.tachiyomi.extension.en.evascans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia

class EvaScans : MangaThemesia(
    "Eva Scans",
    "https://evascans.org",
    "en",
    "/series",
) {
    override fun chapterListSelector(): String = "#chapterlist li:not(:has(svg))"
}
