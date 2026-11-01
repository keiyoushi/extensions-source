package eu.kanade.tachiyomi.extension.vi.tulinhtruyen

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
class MangaListResponse(
    val data: List<MangaDto> = emptyList(),
    val total: Int = 0,
    val offset: Int = 0,
    val limit: Int = 20,
)

@Serializable
class MangaDetailResponse(
    val data: MangaDto,
)

@Serializable
class MangaDto(
    private val id: String,
    private val attributes: MangaAttributes,
    val relationships: List<RelationshipDto> = emptyList(),
) {
    fun toSManga(apiUrl: String): SManga = SManga.create().apply {
        url = "/manga/$id"
        title = attributes.title.preferred()
        thumbnail_url = coverFileName(apiUrl, id)
    }

    fun toSManga(apiUrl: String, existingUrl: String): SManga = SManga.create().apply {
        url = existingUrl
        title = attributes.title.preferred()
        description = buildString {
            val altNames = attributes.altTitles
                .map { it.preferred() }
                .filter { it.isNotBlank() }
                .distinct()
            if (altNames.isNotEmpty()) {
                append("Tên khác: ")
                append(altNames.joinToString("; "))
                append("\n\n")
            }
            append(attributes.description?.preferred().orEmpty())
        }.trim().ifEmpty { null }
        thumbnail_url = coverFileName(apiUrl, id)
        genre = attributes.tags.mapNotNull { it.attributes.name["en"] ?: it.attributes.name["vi"] }
            .joinToString()
        author = relationships.authorNames()
        status = when (attributes.status) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            "cancelled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }

    private fun coverFileName(apiUrl: String, mangaId: String): String? {
        val fileName = relationships
            .firstOrNull { it.type == "cover_art" }
            ?.attributes?.fileName
            ?: return null
        return if (fileName.startsWith("http")) {
            fileName
        } else {
            "$apiUrl/covers/$mangaId/$fileName"
        }
    }
}

@Serializable
class MangaAttributes(
    val title: Map<String, String> = emptyMap(),
    val altTitles: List<Map<String, String>> = emptyList(),
    val description: Map<String, String>? = null,
    val status: String? = null,
    val tags: List<TagDto> = emptyList(),
)

@Serializable
class TagDto(
    val attributes: TagAttributes,
)

@Serializable
class TagAttributes(
    val name: Map<String, String> = emptyMap(),
)

@Serializable
class RelationshipDto(
    val id: String? = null,
    val type: String,
    val attributes: RelationshipAttributes? = null,
)

@Serializable
class RelationshipAttributes(
    val fileName: String? = null,
    val name: String? = null,
    val username: String? = null,
)

private fun Map<String, String>.preferred(): String = this["vi"] ?: this["en"] ?: values.firstOrNull() ?: ""

private fun List<RelationshipDto>.authorNames(): String? = filter { it.type == "author" }
    .mapNotNull { it.attributes?.name }
    .distinct()
    .joinToString()
    .ifEmpty { null }

@Serializable
class ChapterListResponse(
    val data: List<ChapterDto> = emptyList(),
)

@Serializable
class ChapterDetailResponse(
    val data: ChapterDto,
)

@Serializable
class ChapterDto(
    val id: String,
    val attributes: ChapterAttributes,
    val relationships: List<RelationshipDto> = emptyList(),
) {
    fun toSChapter(): SChapter = SChapter.create().apply {
        url = "/at-home/server/$id"
        val number = attributes.chapter?.takeIf { it.isNotBlank() && it != "null" }
        val chapterTitle = attributes.title?.takeIf { it.isNotBlank() && it != "null" }
        name = when {
            number != null && chapterTitle != null -> "Chương $number: $chapterTitle"
            number != null -> "Chương $number"
            chapterTitle != null -> chapterTitle
            else -> "Chương"
        }
        chapter_number = number?.toFloatOrNull() ?: -1f
        date_upload = Instant.tryParse(attributes.publishAt)
        scanlator = uploaderName()
    }

    private fun uploaderName(): String? {
        var name = relationships.firstOrNull { it.type == "scanlation_group" }?.attributes?.name
        if (name == null) {
            name = relationships.firstOrNull { it.type == "user" }?.attributes?.username
        }
        if (name.isNullOrBlank()) {
            name = attributes.uploaderName
        }
        return name
    }
}

@Serializable
class ChapterAttributes(
    val chapter: String? = null,
    val title: String? = null,
    val publishAt: String? = null,
    val uploaderName: String? = null,
)

@Serializable
class AtHomeResponse(
    val baseUrl: String? = null,
    val chapter: AtHomeChapter,
)

@Serializable
class AtHomeChapter(
    val hash: String,
    val data: List<String> = emptyList(),
)
