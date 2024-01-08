package eu.kanade.tachiyomi.extension.uk.honeymanga.dtos

import kotlinx.serialization.Serializable

@Serializable
data class HoneyMangaDto(
    val id: String,
    val posterId: String,
    val title: String,
)

@Serializable
data class CompleteHoneyMangaDto(
    val id: String,
    val posterId: String,
    val title: String,
    val description: String?,
    val type: String,
    val authors: List<String>? = null,
    val artists: List<String>? = null,
    val genresAndTags: List<String>? = null,
    val titleStatus: String? = null,
)

@Serializable
data class HoneyMangaResponseDto(
    val data: List<HoneyMangaDto>,
)

@Serializable
data class HoneyMangaChapterPagesDto(
    val id: String,
    val resourceIds: Map<String, String>,
)

@Serializable
data class HoneyMangaChapterResponseDto(
    val data: List<HoneyMangaChapterDto>,
)

@Serializable
data class HoneyMangaChapterDto(
    val id: String,
    val volume: Int,
    val chapterNum: Int,
    val subChapterNum: Int,
    val mangaId: String,
    val lastUpdated: String,
)
