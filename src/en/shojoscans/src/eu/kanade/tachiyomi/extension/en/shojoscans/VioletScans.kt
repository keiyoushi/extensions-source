package eu.kanade.tachiyomi.extension.en.shojoscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia

class VioletScans : MangaThemesia(
    "Violet Scans",
    "https://violetscans.org",
    "en",
    mangaUrlDirectory = "/comics",
) {
    override val id = 9079184529211162476

    override fun chapterListSelector(): String = "#chapterlist li:not(:has(svg))"
}
