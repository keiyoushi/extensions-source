package eu.kanade.tachiyomi.extension.en.mehgazone.dto

import kotlinx.serialization.Serializable

@Serializable
data class PageListDto(
    val link: String,
    val content: RenderedDto,
    val excerpt: RenderedDto,
)
