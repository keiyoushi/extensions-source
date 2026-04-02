package eu.kanade.tachiyomi.extension.id.noromax

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia

class Noromax :
    MangaThemesia(
        "Noromax",
        "https://noromax02.my.id",
        "id",
    ) {

    // Site changed from ZeistManga to MangaThemesia
    override val versionId = 2

    override val hasProjectPage = true

    override val pageSelector = "div#readerarea img:not(noscript img)"
}
