package eu.kanade.tachiyomi.extension.en.sirenscans

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ChaptersResponseDto(
    @SerialName("rows_html") val rowsHtml: String,
)

@Serializable
class GenreDto(val name: String, val slug: String)

@Serializable
class GenreResponseDto(val genres: List<GenreDto>)
