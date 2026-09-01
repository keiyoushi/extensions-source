package eu.kanade.tachiyomi.extension.vi.tranh18

import kotlinx.serialization.Serializable

@Serializable
class FilterOption(
    val name: String,
    val value: String,
)

@Serializable
class FilterData(
    val tags: List<FilterOption>,
    val areas: List<FilterOption>,
    val end: List<FilterOption>,
)
