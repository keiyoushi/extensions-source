package eu.kanade.tachiyomi.extension.pt.animexnovel

import kotlinx.serialization.Serializable

@Serializable
class ChapterWrapperDto(
    val items: List<ChapterDto>,
)

@Serializable
class ChapterDto(
    val title: String,
    val published: String,
    val url: String,
)
