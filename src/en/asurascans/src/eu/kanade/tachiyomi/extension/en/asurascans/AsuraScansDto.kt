package eu.kanade.tachiyomi.extension.en.asurascans

import kotlinx.serialization.Serializable

@Serializable
class FiltersDto(
    val genres: List<FilterItemDto>,
    val statuses: List<FilterItemDto>,
    val types: List<FilterItemDto>,
)

@Serializable
class FilterItemDto(
    val id: Int,
    val name: String,
)

@Serializable
class PageDto(
    val order: Int,
    val url: String,
)
