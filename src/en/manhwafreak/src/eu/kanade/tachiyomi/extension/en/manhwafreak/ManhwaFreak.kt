package eu.kanade.tachiyomi.extension.en.manhwafreak

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia

class ManhwaFreak : MangaThemesia(
    "Manhwa Freak",
    "https://manhwafreak.xyz",
    "en",
) {
    override val seriesStatusSelector = ".status-value"
}
