package eu.kanade.tachiyomi.extension.all.ososedki

import kotlinx.serialization.Serializable

@Serializable
class AlbumsResponseDto(
    val html: String,
    val hasMore: Boolean = false,
)
