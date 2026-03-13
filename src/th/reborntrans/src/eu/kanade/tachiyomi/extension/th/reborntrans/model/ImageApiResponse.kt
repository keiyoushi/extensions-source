package eu.kanade.tachiyomi.extension.th.reborntrans.model

import kotlinx.serialization.Serializable

@Serializable
data class ImageApiResponse(
    val success: Boolean = false,
    val assets: List<ImageAsset> = emptyList(),
)

@Serializable
data class ImageAsset(
    val url: String,
    val filename: String = "",
    val alt: String = "",
)
