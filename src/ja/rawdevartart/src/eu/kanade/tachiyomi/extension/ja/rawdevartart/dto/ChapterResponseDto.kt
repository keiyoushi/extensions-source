package eu.kanade.tachiyomi.extension.ja.rawdevartart.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChapterDetailDto(
    @SerialName("chapter_id") val id: String,
    @SerialName("chapter_title") val title: String,
    @SerialName("chapter_number") val number: Float,
    @SerialName("chapter_date_published") val datePublished: String,
    @SerialName("chapter_content") val content: String? = null,
    val server: String,
)

@Serializable
data class ChapterResponseDto(
    @SerialName("chapter_detail") val detail: ChapterDetailDto,
)
