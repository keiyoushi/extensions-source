package eu.kanade.tachiyomi.extension.vi.loppytoon

import kotlinx.serialization.Serializable

@Serializable
class SearchResult(
    val slug: String? = null,
    val title: String? = null,
    val cover: String? = null,
)

@Serializable
class ChapterResponse(
    val html: String? = null,
    val has_more: Boolean = false,
)
