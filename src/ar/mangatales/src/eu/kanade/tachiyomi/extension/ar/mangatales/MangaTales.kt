package eu.kanade.tachiyomi.extension.ar.mangatales

import eu.kanade.tachiyomi.multisrc.gmanga.Gmanga

class MangaTales : Gmanga(
    "Manga Tales",
    "https://www.mangatales.com",
    "ar",
    "https://media.mangatales.com",
) {
    override fun createThumbnail(mangaId: String, cover: String): String {
        return "$cdnUrl/uploads/manga/cover/$mangaId/large_$cover"
    }
}
