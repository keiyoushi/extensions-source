package eu.kanade.tachiyomi.extension.tr.merlinscans

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class MangaIdDto(
    val id: Int,
    val link: String,
)

@Serializable
class ChapterListDto(
    val items: List<ChapterDto>,
    @SerialName("total_pages") val totalPages: Int,
)

@Serializable
class ChapterDto(
    val title: String,
    val number: Float,
    val slug: String,
    @SerialName("created_at") val createdAt: String,
)
