package eu.kanade.tachiyomi.extension.vi.sinhsieusao

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

internal val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
    timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
}

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
    val count: Int,
    val page: Int,
    val pages: Int,
)

@Serializable
class WorkDto(
    val id: Int,
    val kind: String,
    val name: String,
    val names: List<NameDto>,
    @SerialName("author_name") private val authorName: String? = null,
    val description: String? = null,
    @SerialName("cover_url") val coverUrl: String,
    val tags: List<TagDto>,
    val metadata: MetadataDto? = null,
    @SerialName("workable_id") val workableId: Int? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = id.toString()
        title = name
        author = authorName
        description = this@WorkDto.description
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
    @SerialName("latest_chapter") val latestChapter: LatestChapterDto? = null,
)

@Serializable
class LatestChapterDto(
    val id: Int,
    val number: String,
    val name: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
class NameDto(
    val name: String,
    val primary: Boolean = false,
)

@Serializable
class TagDto(
    val id: Int,
    val name: String,
    val slug: String,
    val category: String? = null,
)

@Serializable
class MangaDto(
    val id: Int,
    val name: String,
    val names: List<NameDto>,
    @SerialName("author_name") private val authorName: String? = null,
    val description: String? = null,
    @SerialName("cover_url") val coverUrl: String,
    val tags: List<TagDto>,
    @SerialName("chapters_count") val chaptersCount: Int? = null,
    val chapters: List<ChapterDto> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        title = name
        author = authorName
        description = this@MangaDto.description
        genre = tags.joinToString { it.name }
    }
}

@Serializable
class ChapterDto(
    val id: Int,
    val name: String? = null,
    val number: String,
    val order: Int,
    @SerialName("created_at") private val createdAt: String,
    @SerialName("processing_status") val processingStatus: String? = null,
) {
    fun toSChapter() = SChapter.create().apply {
        url = id.toString()
        chapter_number = number.toFloatOrNull() ?: -1f
        name = this@ChapterDto.name?.ifEmpty { "Chapter $number" } ?: "Chapter $number"
        date_upload = DATE_FORMAT.tryParse(createdAt)
    }
}

@Serializable
class ChapterDetailDto(
    val id: Int,
    val manga: ChapterMangaDto? = null,
    val pages: List<PageDto>,
    @SerialName("previous_chapter") val previousChapter: AdjacentChapterDto? = null,
    @SerialName("next_chapter") val nextChapter: AdjacentChapterDto? = null,
)

@Serializable
class ChapterMangaDto(
    val id: Int,
    val name: String,
    @SerialName("cover_url") val coverUrl: String,
)

@Serializable
class AdjacentChapterDto(
    val id: Int,
    val number: String,
)

@Serializable
class PageDto(
    val id: Int,
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
    val id: Int,
    val name: String,
    val photos: List<PhotoDto>,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
class PhotoDto(
    val id: Int,
    val order: Int,
    @SerialName("image_url") val imageUrl: String,
)

class GenreItem(
    val id: Int,
    val name: String,
    val slug: String,
)
