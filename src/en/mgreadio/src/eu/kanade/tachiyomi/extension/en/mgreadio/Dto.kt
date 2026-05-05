package eu.kanade.tachiyomi.extension.en.mgreadio

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ChapterListDto(
    val items: List<ChapterDto> = emptyList(),
    @SerialName("total_pages")
    val totalPages: Int = 1,
)

@Serializable
internal data class ChapterDto(
    val title: String = "",
    val number: Float = -1f,
    val slug: String = "",
    @SerialName("created_at")
    val createdAt: String = "",
)

@Serializable
internal data class MgreadSearchDto(
    val title: String,
    val url: String,
    val thumb: String? = null,
)
