package eu.kanade.tachiyomi.extension.en.lgbtics

import kotlinx.serialization.Serializable

@Serializable
class TagDto(
    val name: String,
    val slug: String,
)

@Serializable
class FilterDataDto(
    val genres: List<TagDto>,
    val parodies: List<TagDto>,
)
