package eu.kanade.tachiyomi.extension.es.dragontranslationorg

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
)
