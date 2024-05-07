package eu.kanade.tachiyomi.extension.en.mangamo.dto

import kotlinx.serialization.Serializable

@Serializable
data class SeriesDto(
    val id: Int? = null,
    val authors: List<AuthorDto>? = null,
//    val chapterCount: Int? = null,
//    val createdAt: Long? = null,
    val description: String? = null,
    val enabled: Boolean? = null,
    val genres: List<GenreDto>? = null,
    val maxFreeChapterNumber: Int? = null,
//    val maxMeteredReadingChapterNumber: Int? = null,
    val name: String? = null,
    @Suppress("PropertyName")
    val name_lowercase: String? = null,
    val ongoing: Boolean? = null,
    val onlyOnMangamo: Boolean? = null,
    val onlyTransactional: Boolean? = null,
//    val publishDate: Long? = null,
//    val publishers: List<PublisherDto>? = null,
    val releaseStatusTag: String? = null,
    val titleArt: String? = null,
    val updatedAt: Long? = null,
//    val updatedDate: Long? = null,
//    val uuid: String? = null,
)

@Serializable
data class AuthorDto(
    val id: Int,
    val name: String,
)

// @Serializable
// data class PublisherDto(
//    val id: Int,
//    val name: String,
// )

@Serializable
data class GenreDto(
    val id: Int,
    val name: String,
)
