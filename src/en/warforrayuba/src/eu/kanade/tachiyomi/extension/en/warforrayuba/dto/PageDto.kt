package eu.kanade.tachiyomi.extension.en.warforrayuba.dto

import kotlinx.serialization.Serializable

@Serializable
data class PageDto(
    val description: String,
    val src: String,
)
