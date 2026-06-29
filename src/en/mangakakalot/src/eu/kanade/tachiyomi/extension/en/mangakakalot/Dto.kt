package eu.kanade.tachiyomi.extension.en.mangakakalot

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ChapterResponseDto(
    val success: Boolean = false,
    val data: ChapterDataDto? = null,
)

@Serializable
class ChapterDataDto(
    val chapters: List<ChapterDto> = emptyList(),
)

@Serializable
class ChapterDto(
    @SerialName("chapter_name") val name: String? = null,
    @SerialName("chapter_num") val num: Float? = null,
    @SerialName("chapter_slug") val slug: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)
