package eu.kanade.tachiyomi.extension.vi.sangchanhteam

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class SearchDto(
    val title: String,
    val url: String,
    @SerialName("post_type") val postType: String? = null,
    val thumb: String? = null,
)

@Serializable
class GenreOption(
    val name: String,
    val slug: String,
)
