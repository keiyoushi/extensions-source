package eu.kanade.tachiyomi.extension.en.sirenscans

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChaptersResponseDto(
    val success: Boolean = false,
    @SerialName("chapter_count") val chapterCount: Int = 0,
    @SerialName("rows_html") val rowsHtml: String = "",
)

@Serializable
data class GenreDto(val name: String, val slug: String)

@Serializable
data class GenreResponseDto(val genres: List<GenreDto> = emptyList())
