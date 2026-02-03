package eu.kanade.tachiyomi.extension.vi.zettruyen

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ChapterListResponse(
    val success: Boolean = false,
    val data: ChapterData? = null,
)

@Serializable
class ChapterData(
    val chapters: List<ChapterDto> = emptyList(),
    @SerialName("last_page")
    val lastPage: Int = 1,
    @SerialName("current_page")
    val currentPage: Int = 1,
)

@Serializable
class ChapterDto(
    @SerialName("chapter_name")
    val chapterName: String,
    @SerialName("chapter_slug")
    val chapterSlug: String,
    @SerialName("updated_at")
    val updatedAt: String? = null,
)
