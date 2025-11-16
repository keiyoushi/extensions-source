package eu.kanade.tachiyomi.extension.en.bookwalker.dto

import kotlinx.serialization.Serializable

@Serializable
class BookUpdateDto(
    val productName: String,
    val seriesName: String?,
    val productExplanationShort: String?,
    val productExplanationDetails: String,
    val coverImageUrl: String,
    val authors: List<AuthorUpdateDto>,
)

@Serializable
class AuthorUpdateDto(
    val authorName: String,
)
