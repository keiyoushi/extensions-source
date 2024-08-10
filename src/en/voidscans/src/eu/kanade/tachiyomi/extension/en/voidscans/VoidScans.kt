package eu.kanade.tachiyomi.extension.en.voidscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia

class VoidScans : MangaThemesia(
    "Void Scans",
    "https://voidscans.co",
    "en",
) {
    override val seriesStatusSelector = ".status-value"
}
