package eu.kanade.tachiyomi.extension.all.komga.dto

import kotlinx.serialization.Serializable

@Serializable
data class LibraryDto(
    val id: String,
    val name: String,
)

@Serializable
data class SeriesDto(
    val id: String,
    val libraryId: String,
    val name: String,
    val created: String?,
    val lastModified: String?,
    val fileLastModified: String,
    val booksCount: Int,
    val metadata: SeriesMetadataDto,
    val booksMetadata: BookMetadataAggregationDto,
)

@Serializable
data class SeriesMetadataDto(
    val status: String,
    val created: String?,
    val lastModified: String?,
    val title: String,
    val titleSort: String,
    val summary: String,
    val summaryLock: Boolean,
    val readingDirection: String,
    val readingDirectionLock: Boolean,
    val publisher: String,
    val publisherLock: Boolean,
    val ageRating: Int?,
    val ageRatingLock: Boolean,
    val language: String,
    val languageLock: Boolean,
    val genres: Set<String>,
    val genresLock: Boolean,
    val tags: Set<String>,
    val tagsLock: Boolean,
    val totalBookCount: Int? = null,
)

@Serializable
data class BookMetadataAggregationDto(
    val authors: List<AuthorDto> = emptyList(),
    val tags: Set<String> = emptySet(),
    val releaseDate: String?,
    val summary: String,
    val summaryNumber: String,

    val created: String,
    val lastModified: String,
)

@Serializable
data class BookDto(
    val id: String,
    val seriesId: String,
    val seriesTitle: String,
    val name: String,
    val number: Float,
    val created: String?,
    val lastModified: String?,
    val fileLastModified: String,
    val sizeBytes: Long,
    val size: String,
    val media: MediaDto,
    val metadata: BookMetadataDto,
)

@Serializable
data class MediaDto(
    val status: String,
    val mediaType: String,
    val pagesCount: Int,
)

@Serializable
data class PageDto(
    val number: Int,
    val fileName: String,
    val mediaType: String,
)

@Serializable
data class BookMetadataDto(
    val title: String,
    val titleLock: Boolean,
    val summary: String,
    val summaryLock: Boolean,
    val number: String,
    val numberLock: Boolean,
    val numberSort: Float,
    val numberSortLock: Boolean,
    val releaseDate: String?,
    val releaseDateLock: Boolean,
    val authors: List<AuthorDto>,
    val authorsLock: Boolean,
)

@Serializable
data class AuthorDto(
    val name: String,
    val role: String,
)

@Serializable
data class CollectionDto(
    val id: String,
    val name: String,
    val ordered: Boolean,
    val seriesIds: List<String>,
    val createdDate: String,
    val lastModifiedDate: String,
    val filtered: Boolean,
)

@Serializable
data class ReadListDto(
    val id: String,
    val name: String,
    val summary: String,
    val bookIds: List<String>,
    val createdDate: String,
    val lastModifiedDate: String,
    val filtered: Boolean,
)
