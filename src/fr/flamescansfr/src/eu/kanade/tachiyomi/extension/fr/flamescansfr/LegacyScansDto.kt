package eu.kanade.tachiyomi.extension.fr.flamescansfr

import kotlinx.serialization.Serializable

@Serializable
class MangaDto(
    val cover: String?,
    val slug: String,
    val title: String,
) {
    fun isCoverPresent(): Boolean = cover != null
}

@Serializable
class SearchDto(
    val comics: List<MangaDto>,
)

@Serializable
class SearchQueryDto(
    val results: List<MangaDto>,
)
