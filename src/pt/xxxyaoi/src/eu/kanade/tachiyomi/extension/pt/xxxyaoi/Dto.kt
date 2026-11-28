package eu.kanade.tachiyomi.extension.pt.xxxyaoi

import kotlinx.serialization.Serializable

@Serializable
class PageRead(
    val check: Boolean = false,
    val status: Int?,
    val title: String?,
    val result: PageDocument?,
    val finished: List<FinishedRequest> = emptyList(),
)

@Serializable
class FinishedRequest(val url: String, val ago: Int)

@Serializable
class PageDocument(
    val url: String,
    val html: String,
    val session: Map<String, String> = emptyMap(),
    val pages: List<PageStart> = emptyList(),
    val reload: Boolean = false,
)

@Serializable
class PageStart(val length: Int, val hash: String)

@Serializable
class Genre(val name: String, val slug: String)
