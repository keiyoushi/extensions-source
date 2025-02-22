package eu.kanade.tachiyomi.extension.en.zeroscans

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class NewChaptersResponseDto(
    val all: List<NewChaptersMangaDto>,
)

@Serializable
data class NewChaptersMangaDto(
    val slug: String,
)

@Serializable
data class ZeroScansResponseDto<T>(
    val success: Boolean? = null,
    val data: T,
    val message: String? = null,
)

@Serializable
data class ZeroScansComicsDataDto(
    val comics: List<ZeroScansComicDto>,
    val genres: List<ZeroScansGenreDto>,
    val statuses: List<ZeroScansStatusDto>,
    val rankings: ZeroScansRankingsDto,
)

@Serializable
data class ZeroScansComicDto(
    val name: String,
    val slug: String,
    val id: Int,
    val cover: ZeroScansCoverDto,
    val summary: String,
    val statuses: List<ZeroScansStatusDto>,
    val genres: List<ZeroScansGenreDto>,
    @SerialName("chapter_count") val chapterCount: Int,
    @SerialName("bookmark_count") val bookmarkCount: Int,
    @SerialName("view_count") val viewCount: Int,
    val rating: JsonElement,
) {
    fun getRating(): Float {
        return this.rating.toString().toFloatOrNull() ?: 0F
    }
}

@Serializable
data class ZeroScansGenreDto(
    val name: String,
    val id: Int,
)

@Serializable
data class ZeroScansStatusDto(
    val name: String,
    val id: Int,
)

@Serializable
data class ZeroScansRankingsDto(
    @SerialName("all_time") val allTime: List<ZeroScansRankingsEntryDto>,
    @SerialName("weekly_comics") val weekly: List<ZeroScansRankingsEntryDto>,
    @SerialName("monthly_comics") val monthly: List<ZeroScansRankingsEntryDto>,
)

@Serializable
data class ZeroScansRankingsEntryDto(
    val slug: String,
)

@Serializable
data class ZeroScansCoverDto(
    val horizontal: String? = null,
    val vertical: String? = null,
    val full: String? = null,
) {
    fun getHighResCover(): String {
        return when {
            !this.full.isNullOrBlank() -> this.full
            !this.horizontal.isNullOrBlank() -> this.horizontal.replace("-horizontal", "-full")
            !this.vertical.isNullOrBlank() -> this.vertical.replace("-vertical", "-full")
            else -> ""
        }
    }
}

@Serializable
data class ZeroScansChaptersResponseDto(
    val data: List<ZeroScansChapterDto> = emptyList(),
    @SerialName("current_page") val currentPage: Int,
    @SerialName("last_page") val lastPage: Int,
)

@Serializable
data class ZeroScansChapterDto(
    val id: Int,
    val name: Int,
    val group: String?,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class ZeroScansPageResponseDto(
    val chapter: ZeroScansChapterPagesDto,
)

@Serializable
data class ZeroScansChapterPagesDto(
    @SerialName("high_quality") val highQuality: List<String> = emptyList(),
    @SerialName("good_quality") val goodQuality: List<String> = emptyList(),
)
