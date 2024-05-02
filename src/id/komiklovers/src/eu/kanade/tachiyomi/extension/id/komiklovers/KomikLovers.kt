package eu.kanade.tachiyomi.extension.id.komiklovers

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia

class KomikLovers : MangaThemesia(
    "Komik Lovers",
    "https://komiklovers.com",
    "id",
    "/komik",
) {
    override val hasProjectPage = true
}
