package eu.kanade.tachiyomi.multisrc.comiciviewer

import kotlinx.serialization.Serializable

@Serializable
class ViewerResponse(
    val result: List<PageDto>,
    val totalPages: Int,
)

@Serializable
class PageDto(
    val imageUrl: String,
    val scramble: String,
    val sort: Int,
)

@Serializable
class TilePos(
    val x: Int,
    val y: Int,
)
