package eu.kanade.tachiyomi.extension.ar.dilar

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

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
class RankingsDto(
    val topSeries: List<SeriesDto>,
)

@Serializable
class SeriesDto(
    private val id: String,
    private val title: String,
    private val cover: String? = null,
    private val summary: String? = null,
    private val staff: List<StaffDto> = emptyList(),
    private val categories: List<NameDto> = emptyList(),
    @SerialName("translation_status") private val translationStatus: String? = null,
    private val seriesType: SeriesTypeDto? = null,
) {

    fun isNovel() = seriesType?.title == "رواية"

    context(source: Dilar)
    fun toSManga() = SManga.create().apply {
        title = this@SeriesDto.title
        url = "$id/$title"
        thumbnail_url = cover?.let { source.createThumbnail(id, it) }
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
class SeriesTypeDto(
    val title: String,
)

@Serializable
class RoleDto(val role: String? = null)

// Search

@Serializable
class SearchRequestDto(
    val query: String,
    val page: Int,
    val seriesType: IncludeExcludeDto<String>,
    val categories: IncludeExcludeDto<Int>,
    val oneshot: Boolean,
    val storyStatus: IncludeExcludeDto<String>,
    val translationStatus: IncludeExcludeDto<String>,
    val over17: Boolean?,
)

@Serializable
class IncludeExcludeDto<T>(
    val include: List<T>,
    val exclude: List<T>,
)

@Serializable
class CategoryGroupDto(
    val name: String,
    val categories: List<CategoryDto>,
)

@Serializable
class CategoryDto(
    val id: String,
    val name: String,
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
    fun toSChapter(chapter: ChapterReleasesDto, mangaUrl: String) = SChapter.create().apply {
        val title = if (!chapter.title.isNullOrBlank()) " - ${chapter.title}" else ""
        val number = chapter.chapter.removeSuffix(".00")
        url = "$mangaUrl/$number#$id"
        name = "$number$title"
        chapter_number = chapter.chapter.toFloatOrNull() ?: -1F
        date_upload = Instant.tryParse(createdAt)
    }
}

// Pages

@Serializable
class PageListDto(
    @SerialName("storage_key") val storageKey: String,
    @SerialName("media_token") val mediaToken: String,
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
