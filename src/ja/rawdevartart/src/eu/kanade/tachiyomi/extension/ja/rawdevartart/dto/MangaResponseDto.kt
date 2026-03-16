package eu.kanade.tachiyomi.extension.ja.rawdevartart.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TagDto(
    @SerialName("tag_name") val name: String,
    @SerialName("tag_id") val id: Int,
)

@Serializable
data class AuthorDto(
    @SerialName("author_name") val name: String,
    @SerialName("author_id") val id: Int,
)

@Serializable
data class ChapterDto(
    @SerialName("chapter_id") val id: String,
    @SerialName("chapter_title") val title: String,
    @SerialName("chapter_number") val number: Float,
    @SerialName("chapter_views") val views: Float,
    @SerialName("chapter_date_published") val datePublished: String,
)

@Serializable
data class MangaResponseDto(
    val detail: MangaDetailDto,
    val tags: List<TagDto>,
    val authors: List<AuthorDto>,
    val chapters: List<ChapterDto>,
)
