package eu.kanade.tachiyomi.extension.id.comicaso

import kotlinx.serialization.Serializable

@Serializable
class CatalogMoreDto(
    val html: String,
    val hasMore: Boolean,
)

@Serializable
class GenreDto(val name: String, val value: String)

@Serializable
class MediaSourceDto(val url: String)
