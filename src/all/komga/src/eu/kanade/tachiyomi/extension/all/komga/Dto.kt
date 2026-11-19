package eu.kanade.tachiyomi.extension.all.komga

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import kotlinx.serialization.Serializable
import org.apache.commons.text.StringSubstitutor
import java.util.Locale

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
    val id: String,
    private val booksCount: Int,
    private val metadata: SeriesMetadataDto,
    private val booksMetadata: BookMetadataAggregationDto,
) {
    fun toSManga(apiRoot: String) = SManga.create().apply {
        title = metadata.title
        url = "$apiRoot/v1/series/$id"
        thumbnail_url = "$url/thumbnail"
        status = when (metadata.status) {
            "ENDED" -> if (metadata.totalBookCount != null && booksCount < metadata.totalBookCount) SManga.PUBLISHING_FINISHED else SManga.COMPLETED
            "ONGOING" -> SManga.ONGOING
            "ABANDONED" -> SManga.CANCELLED
            "HIATUS" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
        genre = (metadata.genres + metadata.tags + booksMetadata.tags).sorted().distinct().joinToString().ifEmpty { null }
        val roles = booksMetadata.authors.byRole()
        setCreators(roles)
        description = MarkdownDescription.Builder()
            .info(
                booksMetadata.releaseDate?.take(4),
                metadata.ageRating?.let { "$it+" },
                Locale.forLanguageTag(metadata.language).displayName,
                if (metadata.totalBookCount != null) {
                    "$booksCount / ${metadata.totalBookCount} books"
                } else if (booksCount == 1) {
                    "1 book"
                } else {
                    "$booksCount books"
                },
            )
            .description(metadata.summary.ifBlank { booksMetadata.summary })
            .field("Publisher", metadata.publisher)
            .otherRoles(roles)
            .altTitles(metadata.alternateTitles.map { it.title })
            .links(metadata.links.map { it.label to it.url })
            .build()
    }
}

@Serializable
class SeriesMetadataDto(
    val title: String,
    val status: String,
    val summary: String,
    val publisher: String,
    val language: String,
    val ageRating: Int?,
    val genres: Set<String>,
    val tags: Set<String>,
    val totalBookCount: Int?,
    val alternateTitles: List<AlternateTitleDto>,
    val links: List<WebLinkDto>,
)

@Serializable
class BookMetadataAggregationDto(
    val authors: List<AuthorDto>,
    val tags: Set<String>,
    val releaseDate: String?,
    val summary: String,
)

@Serializable
class AlternateTitleDto(val title: String)

@Serializable
class WebLinkDto(val label: String, val url: String)

@Serializable
class BookDto(
    val id: String,
    private val seriesTitle: String,
    private val created: String,
    private val sizeBytes: Long,
    private val size: String,
    val media: MediaDto,
    val metadata: BookMetadataDto,
) {
    fun toSManga(apiRoot: String) = SManga.create().apply {
        title = metadata.title
        url = "$apiRoot/v1/books/$id"
        thumbnail_url = "$url/thumbnail"
        genre = metadata.tags.joinToString().ifEmpty { null }
        val roles = metadata.authors.byRole()
        setCreators(roles)
        description = MarkdownDescription.Builder()
            .info(metadata.releaseDate?.take(4), "${media.pagesCount} pages")
            .description(metadata.summary)
            .otherRoles(roles)
            .links(metadata.links.map { it.label to it.url })
            .build()
    }

    fun toSChapter(url: String, isFromReadList: Boolean, mangaTitle: String, chapterNumber: Float, nameTemplate: String, separator: String) = SChapter.create().apply {
        this.url = url
        name = getChapterName(nameTemplate, isFromReadList, mangaTitle)
        chapter_number = chapterNumber
        scanlator = getScanlator(separator)
        date_upload = parseDate(date)
    }

    private fun getChapterName(template: String, isFromReadList: Boolean, mangaTitle: String): String {
        val values = hashMapOf(
            "title" to metadata.title,
            "seriesTitle" to seriesTitle,
            "number" to metadata.number,
            "createdDate" to created,
            "releaseDate" to metadata.releaseDate,
            "size" to size,
            "sizeBytes" to sizeBytes.toString(),
            "pages" to media.pagesCount.toString(),
        )
        val name = buildString {
            if (isFromReadList) {
                append(seriesTitle)
                append(" ")
            }
            append(StringSubstitutor(values, "{", "}").replace(template))
        }
        // Avoid the app trimming the name to empty
        return name.takeIf { it != mangaTitle } ?: "\u200B$mangaTitle"
    }

    private fun getScanlator(separator: String) = metadata.authors
        .filter { it.role == "translator" }
        .joinToString(separator) { it.name }
        .ifEmpty { null }

    private val date get() = metadata.releaseDate ?: created

    // Text EPUBs have no pages
    val isReadable get() = media.mediaProfile != "EPUB" || media.epubDivinaCompatible
}

private fun List<AuthorDto>.byRole() = groupBy({ it.role }, { it.name })

private fun SManga.setCreators(roles: Map<String, List<String>>) {
    author = roles["writer"]?.distinct()?.joinToString()
    artist = roles["penciller"]?.distinct()?.joinToString()
}

// Roles besides writer (author), penciller (artist) and translator (the scanlator)
private fun MarkdownDescription.Builder.otherRoles(roles: Map<String, List<String>>) = apply {
    roles.filterKeys { it != "writer" && it != "penciller" && it != "translator" }
        .forEach { (role, names) -> field(role.replaceFirstChar { it.titlecase() }, names.distinct()) }
}

@Serializable
class MediaDto(
    val mediaProfile: String,
    val epubDivinaCompatible: Boolean,
    val pagesCount: Int,
)

@Serializable
class PageDto(
    val number: Int,
    val mediaType: String,
)

@Serializable
class BookMetadataDto(
    val title: String,
    val summary: String,
    val number: String,
    val numberSort: Float,
    val releaseDate: String?,
    val authors: List<AuthorDto>,
    val tags: Set<String>,
    val links: List<WebLinkDto>,
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
    val ordered: Boolean,
)

@Serializable
class ReadListDto(
    private val id: String,
    private val name: String,
    private val summary: String,
) {
    fun toSManga(apiRoot: String) = SManga.create().apply {
        title = name
        url = "$apiRoot/v1/readlists/$id"
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
    private val language: OperatorDto<String>? = null,
    private val sharingLabel: OperatorDto<String>? = null,
    private val ageRating: OperatorDto<Int>? = null,
    private val releaseDate: OperatorDto<String>? = null,
    private val complete: OperatorDto<String>? = null,
    private val oneShot: OperatorDto<String>? = null,
    private val author: OperatorDto<AuthorMatchDto>? = null,
)

@Serializable
class OperatorDto<T>(
    private val operator: String,
    private val value: T? = null,
    private val dateTime: String? = null,
)

@Serializable
class AuthorMatchDto(private val name: String, private val role: String)
