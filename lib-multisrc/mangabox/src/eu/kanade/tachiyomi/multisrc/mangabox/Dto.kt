package eu.kanade.tachiyomi.multisrc.mangabox

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ApiResponse(
    val success: Boolean = false,
    val data: ApiDataResponse?,
)

@Serializable
class ApiDataResponse(
    val chapters: List<ApiChapter>,
)

@Serializable
class ApiChapter(
    @SerialName("chapter_name") val chapterName: String?,
    @SerialName("chapter_slug") val chapterSlug: String?,
    @SerialName("chapter_num") val chapterNum: Float?,
    @SerialName("updated_at") val updatedAt: String?,
)
