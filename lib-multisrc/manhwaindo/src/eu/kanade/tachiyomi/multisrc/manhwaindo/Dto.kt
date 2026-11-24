package eu.kanade.tachiyomi.multisrc.manhwaindo

import kotlinx.serialization.Serializable

@Serializable
class ChapterDto(
    val id: Long,
    val url: String,
    val chapter: String,
    val time: String,
    val date: Long?,
)

@Serializable
class GenreDto(
    val name: String,
    val value: String,
)
