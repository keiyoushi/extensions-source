package eu.kanade.tachiyomi.extension.vi.vlogtruyen

import kotlinx.serialization.Serializable

@Serializable
data class ChapterDTO(
    val status: Boolean,
    val data: Data,
)

@Serializable
class Data(
    val chaptersHtml: String,
)
