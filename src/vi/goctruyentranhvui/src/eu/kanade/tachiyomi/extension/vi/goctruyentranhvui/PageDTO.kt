package eu.kanade.tachiyomi.extension.vi.goctruyentranhvui

import kotlinx.serialization.Serializable

@Serializable
class ChapterWrapper(
    val headers: Map<String, String> = emptyMap(),
    val body: ChapterBody,
)

@Serializable
class ChapterBody(
    val result: ResultContent,
)

@Serializable
class ResultContent(
    val data: List<String>,
)
