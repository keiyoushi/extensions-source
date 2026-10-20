package eu.kanade.tachiyomi.extension.vi.suicaodex

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

@Serializable
class ListResponse<T>(
    val data: List<T>,
    val meta: Meta,
)

@Serializable
class Meta(
    val page: Int,
    val limit: Int,
    val total: Int,
    val totalPages: Int,
)

@Serializable
class TagDto(
    val id: String,
    val group: String,
    val name: String,
)

@Serializable
class PersonDto(
    val id: String,
    val name: String,
)

@Serializable
class CoverDto(
    val url: String,
)

@Serializable
class GroupDto(
    val name: String,
)

@Serializable
class MangaDto(
    val id: String,
    val slug: String,
    val title: String,
    val description: String? = null,
    val status: String? = null,
    val year: Int? = null,
    val cover: CoverDto? = null,
    val tags: List<TagDto> = emptyList(),
    val authors: List<PersonDto> = emptyList(),
    val artists: List<PersonDto> = emptyList(),
) {
    fun toSManga(): SManga = SManga.create().apply {
        url = this@MangaDto.id
        title = this@MangaDto.title
        thumbnail_url = cover?.url
        this.description = this@MangaDto.description
        author = authors.joinToString { it.name }.ifEmpty { null }
        artist = artists.joinToString { it.name }.ifEmpty { null }
        genre = tags.joinToString { it.name }.ifEmpty { null }
        status = parseStatus(this@MangaDto.status)
        memo = buildJsonObject { put("slug", this@MangaDto.slug) }
    }

    fun toListSManga(): SManga = SManga.create().apply {
        url = this@MangaDto.id
        title = this@MangaDto.title
        thumbnail_url = cover?.url
        memo = buildJsonObject { put("slug", this@MangaDto.slug) }
    }
}

@Serializable
class ChapterDto(
    val id: String,
    val title: String? = null,
    val language: String? = null,
    val volume: String? = null,
    val number: String? = null,
    val publishedAt: String? = null,
    val groups: List<GroupDto> = emptyList(),
) {
    fun toSChapter(): SChapter = SChapter.create().apply {
        url = this@ChapterDto.id
        name = buildString {
            volume?.let { append("Vol. $it ") }
            number?.let { append("Ch. $it") }
            title?.takeIf { it.isNotBlank() }?.let {
                if (isNotEmpty()) append(" - ")
                append(it)
            }
            if (isEmpty()) append("Chapter")
        }
        chapter_number = number?.toFloatOrNull() ?: -1f
        date_upload = Instant.tryParse(publishedAt)
        scanlator = groups.joinToString { it.name }.ifEmpty { null }
    }
}

@Serializable
class ChapterMangaRef(
    val id: String,
    val slug: String,
    val title: String,
    val cover: CoverDto? = null,
)

@Serializable
class PageDto(
    val position: Int,
    val url: String? = null,
    val replicaUrl: String? = null,
)

@Serializable
class ChapterDetailDto(
    val id: String,
    val manga: ChapterMangaRef? = null,
    val pages: List<PageDto> = emptyList(),
)

private fun parseStatus(status: String?): Int = when (status) {
    "ongoing" -> SManga.ONGOING
    "completed" -> SManga.COMPLETED
    "hiatus" -> SManga.ON_HIATUS
    "cancelled" -> SManga.CANCELLED
    else -> SManga.UNKNOWN
}
