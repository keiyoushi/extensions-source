package eu.kanade.tachiyomi.extension.all.batotov4

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class GraphQL<T>(
    private val variables: T,
    private val query: String,
)

@Serializable
class SearchVariables(
    private val select: SearchPayload,
)

@Serializable
class SearchPayload(
    @SerialName("word") private val query: String? = null,
    private val incGenres: List<String>? = null,
    private val excGenres: List<String>? = null,
    private val incTLangs: List<String>? = null,
    private val incOLangs: List<String>? = null,
    private val sortby: String? = null,
    private val chapCount: String? = null,
    private val origStatus: String? = null,
    private val siteStatus: String? = null,
    private val page: Int,
    private val size: Int,
    private val where: String = "browse",
)

@Serializable
class IdVariables(
    private val id: String,
)

// --- Pager response DTOs (for get_comic_browse_pager) ---

@Serializable
data class PagerResponse(
    val data: PagerData,
)

@Serializable
data class PagerData(
    @SerialName("get_comic_browse_pager")
    val pager: ComicBrowsePager,
)

@Serializable
data class ComicBrowsePager(
    val total: Long,
    val pages: Int,
    val page: Int,
    val init: Int,
    val size: Int,
    val skip: Int,
    val limit: Int,
    val prev: Int, // 0 when not available
    val next: Int,
)
