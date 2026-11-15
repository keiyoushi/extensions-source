package eu.kanade.tachiyomi.extension.th.nekohentai

import kotlinx.serialization.Serializable

@Serializable
class FilterDataDto(
    val categories: List<String>,
    val genres: List<GenreDto>,
)

@Serializable
class GenreDto(
    val id: String,
    val name: String,
)
