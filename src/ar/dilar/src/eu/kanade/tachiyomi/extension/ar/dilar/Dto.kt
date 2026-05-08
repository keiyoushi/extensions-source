package eu.kanade.tachiyomi.extension.ar.dilar

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat

// ── Search ────────────────────────────────────────────────────────────────

@Serializable
class DilarSearchPayload(
    val query: String,
    val includes: List<String> = listOf("Manga", "Team", "Member"),
)

@Serializable
class DilarSearchGroupDto(
    @SerialName("class") val clazz: String,
    val data: List<DilarSearchItemDto> = emptyList(),
)

@Serializable
class DilarSearchItemDto(
    val id: String,
    val title: String? = null,
    val cover: String? = null,
) {
    fun toSManga(cdnUrl: String) = SManga.create().apply {
        val safeTitle = this@DilarSearchItemDto.title?.trim()?.ifBlank { null } ?: "Unknown"
        this.title = safeTitle
        url = "/series/$id/$safeTitle"
        thumbnail_url = cover?.let { "$cdnUrl/uploads/manga/cover/$id/large_$it" }
    }
}

// ── Series list ───────────────────────────────────────────────────────────

@Serializable
class DilarSeriesListDto(
    val series: List<DilarSeriesItemDto> = emptyList(),
    val currentPage: Int = 1,
    val totalPages: Int = 1,
) {
    val hasNextPage get() = currentPage < totalPages
}

@Serializable
class DilarSeriesItemDto(
    val id: String,
    val title: String? = null,
    val cover: String? = null,
) {
    fun toSManga(cdnUrl: String) = SManga.create().apply {
        val safeTitle = this@DilarSeriesItemDto.title?.trim()?.ifBlank { null } ?: "Unknown"
        this.title = safeTitle
        url = "/series/$id/$safeTitle"
        thumbnail_url = cover?.let { "$cdnUrl/uploads/manga/cover/$id/large_$it" }
    }
}

// ── Series detail ─────────────────────────────────────────────────────────

@Serializable
class DilarSeriesDto(
    val id: String,
    val title: String = "",
    val summary: String? = null,
    val cover: String? = null,
    val staff: List<DilarStaffDto> = emptyList(),
    val categories: List<DilarCategoryDto> = emptyList(),
    @SerialName("translation_status") val translationStatus: String? = null,
) {
    fun toSManga(cdnUrl: String) = SManga.create().apply {
        this.title = this@DilarSeriesDto.title.ifBlank { "Unknown" }
        url = "/series/${this@DilarSeriesDto.id}/${this.title}"
        description = summary
        thumbnail_url = cover?.let { "$cdnUrl/uploads/manga/cover/${this@DilarSeriesDto.id}/large_$it" }
        author = staff.filter { it.staffRole?.role == "Author" }.joinToString { it.name }
        artist = staff.filter { it.staffRole?.role == "Artist" }.joinToString { it.name }
            .ifBlank { author }
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
class DilarStaffDto(
    val name: String,
    @SerialName("Staff") val staffRole: DilarStaffRoleDto? = null,
)

@Serializable
class DilarStaffRoleDto(
    val role: String? = null,
    @SerialName("series_id") val seriesId: String? = null,
    @SerialName("mangaka_id") val mangakaId: String? = null,
)

@Serializable
class DilarCategoryDto(
    val name: String,
    @SerialName("Categorization") val categorization: DilarCategorizationDto? = null,
)

@Serializable
class DilarCategorizationDto(
    @SerialName("series_id") val seriesId: String? = null,
    @SerialName("category_id") val categoryId: String? = null,
)

// ── Chapter list ──────────────────────────────────────────────────────────

@Serializable
class DilarChapterListDto(
    val chapters: List<DilarChapterWithReleasesDto> = emptyList(),
)

@Serializable
class DilarChapterWithReleasesDto(
    val id: String,
    val chapter: String? = null,
    val title: String? = null,
    val releases: List<DilarReleaseDto> = emptyList(),
)

@Serializable
class DilarReleaseDto(
    val id: String,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("link_control") val linkControl: Int = 0,
    val teams: List<DilarTeamDto> = emptyList(),
) {
    fun toSChapter(chapter: DilarChapterWithReleasesDto, dateFormat: SimpleDateFormat) = SChapter.create().apply {
        url = "/releases/$id"
        chapter_number = chapter.chapter?.toFloatOrNull() ?: -1f
        name = buildString {
            chapter.chapter?.toFloatOrNull()?.toInt()?.let { append("فصل $it") }
            if (!chapter.title.isNullOrBlank()) append(" - ${chapter.title}")
        }.ifEmpty { "فصل ${chapter.id}" }
        scanlator = teams.joinToString { it.name }
        date_upload = dateFormat.tryParse(createdAt)
    }
}

@Serializable
class DilarTeamDto(
    val name: String,
)

// ── Pages ─────────────────────────────────────────────────────────────────

@Serializable
class DilarReleaseDetailDto(
    val id: String,
    @SerialName("storage_key") val storageKey: String,
    val pages: List<DilarPageDto> = emptyList(),
)

@Serializable
class DilarPageDto(
    val url: String,
    val order: Int,
)
