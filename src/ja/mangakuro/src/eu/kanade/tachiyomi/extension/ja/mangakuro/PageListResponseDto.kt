package eu.kanade.tachiyomi.extension.ja.mangakuro

import kotlinx.serialization.Serializable

@Serializable
class PageListResponseDto(
    val status: Boolean = false,
    val msg: String? = null,
    val html: String,
)
