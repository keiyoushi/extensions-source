package eu.kanade.tachiyomi.extension.en.duskscans

import kotlinx.serialization.Serializable

@Serializable
data class MangaDto(
    val id: String,
    val title: String,
    val slug: String,
    val description: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val cover: String? = null,
    val status: String? = null,
    val genres: List<String> = emptyList(),
    val updatedAt: String? = null,
    val chapters: List<ChapterDto> = emptyList(),
)

@Serializable
data class ChapterDto(
    val id: String,
    val number: Int,
    val title: String = "",
    val releaseDate: String? = null,
)

@Serializable
data class ChapterDetailDto(
    val id: String,
    val number: Int,
    val pages: String,
)
