package eu.kanade.tachiyomi.extension.en.bookwalker.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("normal")
class SingleDto(
    val detailUrl: String,
    val title: String,
    val imageUrl: String,
    val authors: List<AuthorDto>,
) : HoldBookEntityDto()

@Serializable
class AuthorDto(
    val authorName: String,
)
