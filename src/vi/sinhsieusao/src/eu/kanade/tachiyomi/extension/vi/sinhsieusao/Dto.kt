package eu.kanade.tachiyomi.extension.vi.sinhsieusao

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
class WorksResponse(
    val meta: WorksMeta,
    val items: List<WorkDto>,
)

@Serializable
class WorksMeta(
    val pagy: Pagy,
)

@Serializable
class Pagy(
    val page: Int,
    val pages: Int,
)

@Serializable
class WorkDto(
    val id: Int,
    val kind: String,
    private val name: String,
    @SerialName("author_name") private val authorName: String? = null,
    private val description: String? = null,
    @SerialName("cover_url") private val coverUrl: String,
    private val tags: List<WorkTagDto>,
    private val metadata: MetadataDto? = null,
    @SerialName("workable_id") val workableId: Int? = null,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = id.toString()
        title = name
        author = authorName
        description = this@WorkDto.description
        thumbnail_url = baseUrl + coverUrl
        genre = tags.joinToString { it.name }
        status = if (metadata?.chaptersCount != null && metadata.chaptersCount > 0) {
            SManga.ONGOING
        } else {
            SManga.COMPLETED
        }
    }
}

@Serializable
class MetadataDto(
    @SerialName("chapters_count") val chaptersCount: Int? = null,
)

@Serializable
class WorkTagDto(
    val name: String,
)

@Serializable
class TagDto(
    val name: String,
    val slug: String,
)

@Serializable
class MangaDto(
    val chapters: List<ChapterDto>,
)

@Serializable
class ChapterDto(
    val id: Int,
    val name: String? = null,
    val number: String,
    val order: Int,
    @SerialName("created_at") private val createdAt: String,
    @SerialName("processing_status") val processingStatus: String? = null,
) {
    fun toSChapter(workId: Int) = SChapter.create().apply {
        url = "/works/$workId/chapters/$id"
        chapter_number = number.toFloatOrNull() ?: -1f
        name = this@ChapterDto.name?.ifEmpty { "Chapter $number" } ?: "Chapter $number"
        date_upload = Instant.parseOrNull(createdAt)?.toEpochMilliseconds() ?: 0L
    }
}

@Serializable
class ChapterDetailDto(
    val pages: List<PageDto>,
)

@Serializable
class PageDto(
    val order: Int,
    @SerialName("image_url") val imageUrl: String,
)

@Serializable
class TagsResponse(
    val meta: WorksMeta,
    val items: List<TagDto>,
)

@Serializable
class TopWorksResponse(
    val items: List<WorkDto>,
)

@Serializable
class AlbumResponse(
    private val id: Int,
    val photos: List<PhotoDto>,
    @SerialName("created_at") private val createdAt: String,
) {
    fun toSChapter(workId: Int) = SChapter.create().apply {
        url = "album:$id:$workId"
        name = "Oneshot"
        chapter_number = 1f
        date_upload = Instant.parseOrNull(createdAt)?.toEpochMilliseconds() ?: 0L
    }
}

@Serializable
class PhotoDto(
    val order: Int,
    @SerialName("image_url") val imageUrl: String,
)

@Serializable
class GenreItem(
    val name: String,
    val slug: String,
)
