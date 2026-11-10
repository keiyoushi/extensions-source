package eu.kanade.tachiyomi.extension.en.manhwax

import kotlinx.serialization.Serializable

@Serializable
class ChapterDto(
    val images: List<String>,
)

@Serializable
class GenreDto(
    val name: String,
    val slug: String,
)
