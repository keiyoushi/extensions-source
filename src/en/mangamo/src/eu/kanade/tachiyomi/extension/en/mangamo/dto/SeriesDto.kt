package eu.kanade.tachiyomi.extension.en.mangamo.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SeriesDto(
    val id: Int?,
    val authors: List<AuthorDto>?,
    val chapterCount: Int?,
    val createdAt: Long?,
    val description: String?,
    val enabled: Boolean?,
    val genres: List<GenreDto>?,
    val maxFreeChapterNumber: Int?,
    val maxMeteredReadingChapterNumber: Int?,
    val name: String?,
    @SerialName("name_lowercase")
    val nameLowercase: String?,
    val ongoing: Boolean?,
    val onlyOnMangamo: Boolean?,
    val onlyTransactional: Boolean?,
    val publishDate: Long?,
    val publishers: List<PublisherDto>?,
    val releaseStatusTag: String?,
    val titleArt: String?,
    val updatedAt: Long?,
    val updatedDate: Long?,
    val uuid: String?,
)

@Serializable
data class AuthorDto(
    val id: Int,
    val name: String,
)

@Serializable
data class PublisherDto(
    val id: Int,
    val name: String,
)

@Serializable
data class GenreDto(
    val id: Int,
    val name: String,
)
