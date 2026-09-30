package eu.kanade.tachiyomi.extension.id.mikoroku

import kotlinx.serialization.Serializable

@Serializable
data class CatalogEntry(
    val title: String = "",
    val altTitle: String = "",
    val slug: String = "",
    val img: String = "",
    val desc: String = "",
    val genres: List<String> = emptyList(),
    val rating: Double = 0.0,
    val status: String = "",
    val author: String = "",
    val artist: String = "",
)
