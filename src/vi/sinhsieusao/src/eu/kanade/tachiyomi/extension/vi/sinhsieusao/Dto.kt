package eu.kanade.tachiyomi.extension.vi.sinhsieusao

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

@Serializable
class WorksResponse(
    val meta: WorksMeta,
    val items: List<WorkDto>? = null,
)

@Serializable
class WorksMeta(
    val pagy: Pagy,
)

@Serializable
class TopWorksResponse(
    val items: List<WorkDto>? = null,
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
    @SerialName("cover_url") private val coverUrl: String? = null,
    private val status: String? = null,
    private val tags: List<WorkTagDto>? = null,
    private val metadata: MetadataDto? = null,
    @SerialName("workable_id") val workableId: Int? = null,
) {
    fun toSManga(): SManga = SManga.create().apply {
        url = id.toString()
        title = name
        author = authorName
        description = this@WorkDto.description
        thumbnail_url = this@WorkDto.coverUrl
        genre = tags?.joinToString { it.name }.orEmpty()
        status = when {
            this@WorkDto.status == "completed" -> SManga.COMPLETED
            metadata?.chaptersCount != null || metadata?.photosCount != null -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
        memo = toMemo()
    }

    fun toMemo(): JsonObject = buildJsonObject {
        put("workId", id)
        put("kind", kind)
        workableId?.let { put("workableId", it) }
    }
}

@Serializable
class MetadataDto(
    @SerialName("chapters_count") val chaptersCount: Int? = null,
    @SerialName("photos_count") val photosCount: Int? = null,
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
    private val chapters: List<ChapterDto>? = null,
) {
    fun toSChapters(workId: Int): List<SChapter> = chapters.orEmpty()
        .filter { it.processingStatus == null || it.processingStatus == "processed" }
        .sortedByDescending { it.order }
        .map { it.toSChapter(workId) }
}

@Serializable
class ChapterDto(
    val id: Int,
    private val name: String? = null,
    val number: String,
    val order: Int,
    @SerialName("created_at") private val createdAt: String? = null,
    @SerialName("processing_status") val processingStatus: String? = null,
) {
    private val displayName: String
        get() = name?.takeIf { it.isNotBlank() } ?: "Chapter $number"

    fun toSChapter(workId: Int) = SChapter.create().apply {
        url = "/works/$workId/chapters/$id"
        chapter_number = number.toFloatOrNull() ?: -1f
        this.name = displayName
        date_upload = Instant.tryParse(createdAt)
        memo = buildJsonObject {
            put("chapterId", id)
        }
    }
}

@Serializable
class ChapterDetailDto(
    private val pages: List<PageDto>? = null,
) {
    fun toPages(): List<Page> = pages.orEmpty()
        .sortedBy { it.order }
        .mapIndexed { index, page -> Page(index, imageUrl = page.imageUrl) }
}

@Serializable
class PageDto(
    val order: Int,
    @SerialName("image_url") val imageUrl: String,
)

@Serializable
class TagsResponse(
    val meta: TagsMeta,
    val items: List<TagDto>? = null,
)

@Serializable
class TagsMeta(
    val pagy: Pagy,
)

@Serializable
class AlbumMeta(
    val photos: AlbumPhotosMeta? = null,
)

@Serializable
class AlbumPhotosMeta(
    val total: Int,
)

/**
 * One page of the reader: the album and the photos it currently shows.
 */
@Serializable
class AlbumResponse(
    private val id: Int,
    @SerialName("created_at") private val createdAt: String? = null,
    @SerialName("updated_at") private val updatedAt: String? = null,
    private val meta: AlbumMeta? = null,
    private val photos: List<PhotoDto>? = null,
) {
    val photosTotal: Int get() = meta?.photos?.total ?: photos?.size ?: 0

    val photosList: List<PhotoDto> get() = photos.orEmpty()

    fun toSChapter(workId: Int) = SChapter.create().apply {
        url = "/works/$workId"
        name = "Oneshot"
        chapter_number = 1f
        date_upload = Instant.tryParse(updatedAt) ?: Instant.tryParse(createdAt)
        memo = buildJsonObject {
            put("albumId", id)
        }
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
