package eu.kanade.tachiyomi.extension.en.manhwahentai

import kotlinx.serialization.Serializable

@Serializable
class XhrResponseDto(
    val success: Boolean,
    val data: String,
)

@Serializable
class PageDto(
    val src: String,
)
