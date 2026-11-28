package eu.kanade.tachiyomi.extension.all.silo

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class CatalogDto(
    val items: List<ItemDto>,
    val page: PageDto? = null,
)

@Serializable
class PageDto(
    @SerialName("has_more") val hasMore: Boolean = false,
    @SerialName("next_cursor") val nextCursor: String? = null,
)

@Serializable
class ItemDto(
    @SerialName("content_id") private val contentId: String,
    private val title: String,
    private val overview: String? = null,
    private val genres: List<String> = emptyList(),
    private val studios: List<String> = emptyList(),
    @SerialName("show_status") private val showStatus: String? = null,
    @SerialName("poster_url") private val posterUrl: String? = null,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = contentId
        title = this@ItemDto.title
        thumbnail_url = posterUrl?.let { if (it.startsWith("http")) it else baseUrl + it }
        description = overview
        genre = genres.joinToString().ifEmpty { null }
        author = studios.joinToString().ifEmpty { null }
        status = when (showStatus?.lowercase()) {
            "completed", "ended" -> SManga.COMPLETED
            "ongoing", "continuing" -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class ChapterListDto(private val items: List<ChapterDto>) {
    fun toSChapters() = items
        .sortedByDescending { it.chapterIndex ?: 0.0 }
        .map { it.toSChapter() }
}

@Serializable
class ChapterDto(
    @SerialName("content_id") private val contentId: String,
    private val title: String,
    @SerialName("chapter_index") val chapterIndex: Double? = null,
) {
    fun toSChapter() = SChapter.create().apply {
        url = contentId
        name = title
        chapter_number = (chapterIndex ?: -1.0).toFloat()
    }
}

@Serializable
class VersionsDto(val items: List<VersionDto>)

@Serializable
class VersionDto(@SerialName("file_id") val fileId: String)

@Serializable
class ProfilesDto(val items: List<ProfileDto>)

@Serializable
class ProfileDto(
    val id: String,
    @SerialName("is_primary") val isPrimary: Boolean = false,
)
