package eu.kanade.tachiyomi.extension.id.manhwaindo

import kotlinx.serialization.Serializable

@Serializable
class ChapterDto(
    val url: String,
    val chapter: String,
    val time: String,
)

@Serializable
class GenreDto(
    val name: String,
    val value: String,
)
