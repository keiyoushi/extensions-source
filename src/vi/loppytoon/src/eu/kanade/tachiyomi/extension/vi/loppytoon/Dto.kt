package eu.kanade.tachiyomi.extension.vi.loppytoon

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class SearchResult(
    val slug: String,
    val title: String,
    val cover: String? = null,
)

@Serializable
class ChapterResponse(
    val html: String,
    @SerialName("has_more") val hasMore: Boolean,
)
