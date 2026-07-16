package eu.kanade.tachiyomi.extension.es.emperorscan

import kotlinx.serialization.Serializable

@Serializable
class ChapterListDto(
    val items: List<ChapterDto>,
)

@Serializable
class ChapterDto(
    val name: String,
    val url: String,
    val ago: String,
    val st: String,
)
