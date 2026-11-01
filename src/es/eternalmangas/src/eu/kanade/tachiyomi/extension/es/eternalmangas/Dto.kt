package eu.kanade.tachiyomi.extension.es.eternalmangas

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class SeriesPayload(
    val content: SeriesContent,
)

@Serializable
class SeriesContent(
    val chapters: List<ChapterDto>,
)

@Serializable
class ChapterDto(
    val title: String,
    val url: ChapterUrl,
    @SerialName("published_at") val publishedAt: String? = null,
)

@Serializable
class ChapterUrl(
    val url: String,
)
