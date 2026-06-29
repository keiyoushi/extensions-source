package eu.kanade.tachiyomi.extension.vi.otakusic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

@Serializable
class ChaptersResponse(
    val data: List<ChapterDto> = emptyList(),
)

@Serializable
class ChapterDto(
    @SerialName("chapter_name") val chapterName: JsonPrimitive,
    @SerialName("chapter_slug") val chapterSlug: String,
    @SerialName("chapter_original_slug") val chapterOriginalSlug: String,
    @SerialName("manga_slug") val mangaSlug: String,
    @SerialName("is_locked") val isLocked: Boolean = false,
    @SerialName("api_url") val apiUrl: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("public_at") val publicAt: String? = null,
    val status: String? = null,
)
