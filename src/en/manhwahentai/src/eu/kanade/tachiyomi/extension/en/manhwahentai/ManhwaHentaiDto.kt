package eu.kanade.tachiyomi.extension.en.manhwahentai

import kotlinx.serialization.Serializable

@Serializable
data class XhrResponseDto(
    val success: Boolean,
    val data: String,
)

@Serializable
data class PageDto(
    val src: String,
)
