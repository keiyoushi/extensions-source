package eu.kanade.tachiyomi.multisrc.mangabox

import kotlinx.serialization.Serializable

// Base classes
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
    val chapter_name: String,
    val chapter_slug: String,
    val chapter_num: Float,
    val updated_at: String,
)

@Serializable
class ApiPagination(
    val has_more: Boolean,
)
