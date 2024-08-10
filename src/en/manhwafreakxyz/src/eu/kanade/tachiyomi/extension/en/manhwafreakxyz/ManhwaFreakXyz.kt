package eu.kanade.tachiyomi.extension.en.manhwafreakxyz

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia

class ManhwaFreakXyz : MangaThemesia(
    "Manhwa Freak.xyz",
    "https://manhwafreak.xyz",
    "en",
) {
    override val seriesStatusSelector = ".status-value"
}
