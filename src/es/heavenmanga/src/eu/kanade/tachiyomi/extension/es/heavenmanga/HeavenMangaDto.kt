package eu.kanade.tachiyomi.extension.es.heavenmanga

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PayloadChaptersDto(
    val data: List<ChapterDto>,
)

@Serializable
data class ChapterDto(
    val id: Int,
    val slug: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class PageDto(
    val imgURL: String,
)
