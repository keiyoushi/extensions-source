package eu.kanade.tachiyomi.extension.ar.mangatales

import eu.kanade.tachiyomi.multisrc.gmanga.Gmanga
import eu.kanade.tachiyomi.multisrc.gmanga.TagFilterData
import eu.kanade.tachiyomi.source.model.Filter

class MangaTales : Gmanga(
    "Manga Tales",
    "https://www.mangatales.com",
    "ar",
    "https://media.mangatales.com",
) {
    override fun createThumbnail(mangaId: String, cover: String): String {
        return "$cdnUrl/uploads/manga/cover/$mangaId/large_$cover"
    }

    override fun getTypesFilter() = listOf(
        TagFilterData("1", "عربية", Filter.TriState.STATE_INCLUDE),
        TagFilterData("2", "إنجليزي", Filter.TriState.STATE_INCLUDE),
    )
}
