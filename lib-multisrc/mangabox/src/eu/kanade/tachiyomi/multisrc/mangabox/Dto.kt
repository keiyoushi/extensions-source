package eu.kanade.tachiyomi.multisrc.mangabox

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ApiResponse(
    val data: ApiDataResponse,
)

@Serializable
class ApiDataResponse(
    val chapters: List<ApiChapter>,
    val pagination: ApiPagination,
)

@Serializable
class ApiChapter(
    @SerialName("chapter_name") val chapterName: String,
    @SerialName("chapter_slug") val chapterSlug: String,
    @SerialName("chapter_num") val chapterNum: Float,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
class ApiPagination(
    @SerialName("has_more") val hasMore: Boolean,
)
