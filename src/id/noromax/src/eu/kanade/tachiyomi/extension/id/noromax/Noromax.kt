package eu.kanade.tachiyomi.extension.id.noromax

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia

class Noromax :
    MangaThemesia(
        "Noromax",
        "https://noromax01.my.id",
        "id",
    ) {

    // Site changed from ZeistManga to MangaThemesia
    override val versionId = 2

    override val hasProjectPage = true
}
