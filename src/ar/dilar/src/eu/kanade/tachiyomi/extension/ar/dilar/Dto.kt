package eu.kanade.tachiyomi.extension.ar.dilar

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat

// ── Search ────────────────────────────────────────────────────────────────

@Serializable
data class DilarSearchGroupDto(
    @SerialName("class") val clazz: String,
    val data: List<DilarSearchItemDto> = emptyList(),
)

@Serializable
data class DilarSearchItemDto(
    val id: String,
    val title: String? = null,
    val cover: String? = null,
) {
    fun toSManga(cdnUrl: String) = SManga.create().apply {
        url = "/series/$id/${title.orEmpty()}"
        this.title = this@DilarSearchItemDto.title ?: "Unknown"
        thumbnail_url = cover?.let { "$cdnUrl/uploads/manga/cover/$id/large_$it" }
    }
}

// ── Series list ───────────────────────────────────────────────────────────

@Serializable
data class DilarSeriesListDto(
    val series: List<DilarSearchItemDto> = emptyList(),
    val currentPage: Int = 1,
    val totalPages: Int = 1,
) {
    val hasNextPage get() = currentPage < totalPages
}

// ── Series detail ─────────────────────────────────────────────────────────

@Serializable
data class DilarSeriesDto(
    val id: String,
    val title: String,
    val summary: String? = null,
    val cover: String? = null,
    val staff: List<DilarStaffDto> = emptyList(),
    val categories: List<DilarCategoryDto> = emptyList(),
    @SerialName("translation_status") val translationStatus: String? = null,
) {
    fun toSManga(cdnUrl: String) = SManga.create().apply {
        url = "/series/$id/$title"
        this.title = this@DilarSeriesDto.title.ifBlank { "Unknown" }
        description = summary
        thumbnail_url = cover?.let { "$cdnUrl/uploads/manga/cover/$id/large_$it" }
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
data class DilarStaffDto(
    val name: String,
    @SerialName("Staff") val staffRole: DilarStaffRoleDto? = null,
)

@Serializable
data class DilarStaffRoleDto(
    val role: String? = null,
)

@Serializable
data class DilarCategoryDto(
    val name: String,
)

// ── Releases (chapter list) ───────────────────────────────────────────────

@Serializable
data class DilarReleasesDto(
    val releases: List<DilarReleaseDto> = emptyList(),
)

@Serializable
data class DilarReleaseDto(
    val id: String,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("link_control") val linkControl: Int = 0,
    val chapter: DilarChapterDto? = null,
    val teams: List<DilarTeamDto> = emptyList(),
) {
    fun toSChapter(dateFormat: SimpleDateFormat) = SChapter.create().apply {
        url = "/releases/$id"
        chapter_number = chapter?.chapter?.toFloatOrNull() ?: -1f
        name = buildString {
            chapter?.chapter?.toFloatOrNull()?.toInt()?.let { append("فصل $it") }
            if (!chapter?.title.isNullOrBlank()) append(" - ${chapter?.title}")
        }
        scanlator = teams.joinToString { it.name }
        date_upload = try {
            dateFormat.parse(createdAt ?: "")?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }
}

@Serializable
data class DilarChapterDto(
    val id: String,
    val chapter: String? = null,
    val title: String? = null,
)

@Serializable
data class DilarTeamDto(
    val name: String,
)

// ── Pages ─────────────────────────────────────────────────────────────────

@Serializable
data class DilarReleaseDetailDto(
    val id: String,
    @SerialName("storage_key") val storageKey: String,
    val pages: List<DilarPageDto> = emptyList(),
)

@Serializable
data class DilarPageDto(
    val url: String,
    val order: Int,
)
