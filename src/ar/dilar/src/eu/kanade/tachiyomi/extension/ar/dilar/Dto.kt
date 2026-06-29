package eu.kanade.tachiyomi.extension.ar.dilar

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

// Series

@Serializable
class SeriesListDto(
    val series: List<SeriesDto>,
    private val currentPage: Int,
    private val totalPages: Int,
) {
    val hasNextPage get() = currentPage < totalPages
}

@Serializable
class SeriesDto(
    private val id: String,
    private val title: String,
    private val cover: String? = null,
    private val summary: String? = null,
    private val staff: List<StaffDto> = emptyList(),
    private val categories: List<NameDto> = emptyList(),
    @SerialName("translation_status") private val translationStatus: String? = null,
    @SerialName("series_type_id") private val seriesTypeId: String? = null,
) {

    fun isNovel() = seriesTypeId == "99" // 99 is Novel ID

    fun toSManga(createThumbnail: (String, String) -> String) = SManga.create().apply {
        title = this@SeriesDto.title
        url = "$id/$title"
        thumbnail_url = cover?.let { createThumbnail(id, it) }
        description = summary

        author = staff.filter { it.staff?.role == "Author" }.joinToString { it.name }
        artist = staff.filter { it.staff?.role == "Artist" }.joinToString { it.name }
        genre = categories.joinToString { it.name }
        status = when (translationStatus) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "dropped" -> SManga.CANCELLED
            "hiatus" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class StaffDto(
    val name: String,
    @SerialName("Staff") val staff: RoleDto? = null,
)

@Serializable
class RoleDto(val role: String? = null)

// Search

@Serializable
class SearchRequestDto(
    val query: String,
    val page: Int,
)

@Serializable
class SearchListDto(
    val rows: List<SeriesDto>,
    private val total: Int,
    private val page: Int,
    private val perPage: Int,
) {
    val hasNextPage get() = total > (page * perPage)
}

// Chapters

@Serializable
class ChapterListDto(
    val chapters: List<ChapterReleasesDto>,
)

@Serializable
class ChapterReleasesDto(
    val id: String,
    val chapter: String,
    val releases: List<ReleaseDto>,
    val title: String? = null,
)

@Serializable
class ReleaseDto(
    private val id: String,
    @SerialName("created_at") private val createdAt: String? = null,
) {
    fun toSChapter(chapter: ChapterReleasesDto) = SChapter.create().apply {
        val title = if (!chapter.title.isNullOrBlank()) " - ${chapter.title}" else ""
        val number = chapter.chapter.removeSuffix(".00")
        url = "$number#$id"
        name = "$number$title"
        date_upload = dateFormat.tryParse(createdAt)
    }
}

// Pages

@Serializable
class PageListDto(
    @SerialName("storage_key") val storageKey: String,
    val pages: List<PageDto>,
)

@Serializable
class PageDto(
    val url: String,
    val order: Int,
)

// common

@Serializable
class NameDto(
    val name: String,
)

private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}
