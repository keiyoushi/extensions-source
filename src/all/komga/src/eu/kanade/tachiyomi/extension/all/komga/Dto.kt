package eu.kanade.tachiyomi.extension.all.komga

import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import kotlinx.serialization.Serializable
import org.apache.commons.text.StringSubstitutor

@Serializable
class PageWrapperDto<T>(
    val content: List<T>,
    val last: Boolean,
)

@Serializable
class LibraryDto(
    val id: String,
    val name: String,
)

@Serializable
class SeriesDto(
    private val id: String,
    private val booksCount: Int?,
    private val metadata: SeriesMetadataDto,
    private val booksMetadata: BookMetadataAggregationDto?,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        title = metadata.title
        url = "$baseUrl/api/v1/series/$id"
        thumbnail_url = "$url/thumbnail"
        status = when (metadata.status) {
            "ENDED" -> if (metadata.totalBookCount != null && booksCount != null && booksCount < metadata.totalBookCount) SManga.PUBLISHING_FINISHED else SManga.COMPLETED
            "ONGOING" -> SManga.ONGOING
            "ABANDONED" -> SManga.CANCELLED
            "HIATUS" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
        genre = (metadata.genres + metadata.tags + booksMetadata?.tags.orEmpty()).sorted().distinct().joinToString().ifEmpty { null }
        description = MarkdownDescription.Builder()
            .description(metadata.summary?.ifBlank { null } ?: booksMetadata?.summary)
            .field("Publisher", metadata.publisher)
            .altTitles(metadata.alternateTitles.map { it.title })
            .links(metadata.links.map { it.label to it.url })
            .build()
        booksMetadata?.authors?.groupBy({ it.role }, { it.name })?.let { map ->
            author = map["writer"]?.distinct()?.joinToString()
            artist = map["penciller"]?.distinct()?.joinToString()
        }
    }
}

@Serializable
class SeriesMetadataDto(
    val title: String,
    val status: String?,
    val summary: String?,
    val publisher: String?,
    val genres: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
    val totalBookCount: Int?,
    val alternateTitles: List<AlternateTitleDto> = emptyList(),
    val links: List<WebLinkDto> = emptyList(),
)

@Serializable
class BookMetadataAggregationDto(
    val authors: List<AuthorDto> = emptyList(),
    val tags: Set<String> = emptySet(),
    val summary: String?,
)

@Serializable
class AlternateTitleDto(val title: String)

@Serializable
class WebLinkDto(val label: String, val url: String)

@Serializable
class BookDto(
    val id: String,
    private val seriesTitle: String?,
    private val created: String?,
    private val fileLastModified: String?,
    private val sizeBytes: Long?,
    private val size: String?,
    val media: MediaDto?,
    val metadata: BookMetadataDto,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        title = metadata.title
        url = "$baseUrl/api/v1/books/$id"
        thumbnail_url = "$url/thumbnail"
        genre = metadata.tags.joinToString().ifEmpty { null }
        description = MarkdownDescription.Builder()
            .description(metadata.summary)
            .links(metadata.links.map { it.label to it.url })
            .build()
        author = metadata.authors.map { it.name }.distinct().joinToString().ifEmpty { null }
        artist = author
    }

    fun getChapterName(template: String, isFromReadList: Boolean, mangaTitle: String): String {
        val values = hashMapOf(
            "title" to metadata.title,
            "seriesTitle" to seriesTitle,
            "number" to metadata.number,
            "createdDate" to created,
            "releaseDate" to metadata.releaseDate,
            "size" to size,
            "sizeBytes" to sizeBytes?.toString(),
        )
        val name = buildString {
            if (isFromReadList) {
                append(seriesTitle)
                append(" ")
            }
            append(StringSubstitutor(values, "{", "}").replace(template))
        }
        // Avoid the app trimming the name to empty
        return name.takeIf { it != mangaTitle } ?: "​$mangaTitle"
    }

    fun getScanlator(separator: String) = metadata.authors
        .filter { it.role == "translator" }
        .joinToString(separator) { it.name }
        .ifEmpty { null }

    // fileLastModified is in the server's time zone, not UTC
    val date get() = metadata.releaseDate ?: created ?: fileLastModified
}

@Serializable
class MediaDto(
    val mediaProfile: String?,
    val epubDivinaCompatible: Boolean = false,
)

@Serializable
class PageDto(
    val number: Int,
    val mediaType: String,
)

@Serializable
class BookMetadataDto(
    val title: String,
    val summary: String?,
    val number: String?,
    val numberSort: Float?,
    val releaseDate: String?,
    val authors: List<AuthorDto> = emptyList(),
    val tags: Set<String> = emptySet(),
    val links: List<WebLinkDto> = emptyList(),
)

@Serializable
class AuthorDto(
    val name: String,
    val role: String,
)

@Serializable
class CollectionDto(
    val id: String,
    val name: String,
    val ordered: Boolean = false,
)

@Serializable
class ReadListDto(
    private val id: String,
    private val name: String,
    private val summary: String?,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        title = name
        url = "$baseUrl/api/v1/readlists/$id"
        thumbnail_url = "$url/thumbnail"
        description = MarkdownDescription.Builder().description(summary).build()
    }
}

@Serializable
class SearchDto(
    private val condition: ConditionDto,
    private val fullTextSearch: String? = null,
)

@Serializable
class ConditionDto(
    private val allOf: List<ConditionDto>? = null,
    private val anyOf: List<ConditionDto>? = null,
    private val deleted: OperatorDto<String>? = null,
    private val libraryId: OperatorDto<String>? = null,
    private val collectionId: OperatorDto<String>? = null,
    private val seriesId: OperatorDto<String>? = null,
    private val mediaStatus: OperatorDto<String>? = null,
    private val readStatus: OperatorDto<String>? = null,
    private val seriesStatus: OperatorDto<String>? = null,
    private val genre: OperatorDto<String>? = null,
    private val tag: OperatorDto<String>? = null,
    private val publisher: OperatorDto<String>? = null,
    private val author: OperatorDto<AuthorMatchDto>? = null,
)

@Serializable
class OperatorDto<T>(private val operator: String, private val value: T? = null)

@Serializable
class AuthorMatchDto(private val name: String, private val role: String)
