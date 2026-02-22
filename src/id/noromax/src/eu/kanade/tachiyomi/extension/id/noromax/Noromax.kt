package eu.kanade.tachiyomi.extension.id.noromax

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia

class Noromax :
    MangaThemesia(
        "Noromax",
        "https://noromax02.my.id",
        "id",
    ) {

    // Site changed from MangaThemesia to Noromax
    override val versionId = 3

    override val hasProjectPage = true
}
