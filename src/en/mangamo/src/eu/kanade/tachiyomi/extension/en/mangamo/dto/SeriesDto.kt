package eu.kanade.tachiyomi.extension.en.mangamo.dto

import kotlinx.serialization.Serializable

@Serializable
class SeriesDto(
    val id: Int? = null,
    val authors: List<AuthorDto>? = null,
    val description: String? = null,
    val enabled: Boolean? = null,
    val genres: List<GenreDto>? = null,
    val maxFreeChapterNumber: Int? = null,
    val maxMeteredReadingChapterNumber: Int? = null,
    val name: String? = null,
    @Suppress("PropertyName")
    val name_lowercase: String? = null,
    val ongoing: Boolean? = null,
    val onlyOnMangamo: Boolean? = null,
    val onlyTransactional: Boolean? = null,
    val releaseStatusTag: String? = null,
    val titleArt: String? = null,
    val updatedAt: Long? = null,
)

@Serializable
class AuthorDto(
    val id: Int,
    val name: String,
)

@Serializable
class GenreDto(
    val id: Int,
    val name: String,
)
