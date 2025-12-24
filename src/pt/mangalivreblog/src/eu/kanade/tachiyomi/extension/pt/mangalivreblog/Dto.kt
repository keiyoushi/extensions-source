package eu.kanade.tachiyomi.extension.pt.mangalivreblog

import kotlinx.serialization.Serializable

@Serializable
data class PopularResponse(
    val success: Boolean,
    val data: PopularData,
)

@Serializable
data class PopularData(
    val html: String,
    val period: String,
)
