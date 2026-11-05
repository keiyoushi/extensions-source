package eu.kanade.tachiyomi.extension.en.mangastash

import kotlinx.serialization.Serializable

@Serializable
class FilterData(
    val lists: List<FilterOption>,
    val sorts: List<FilterOption>,
    val sections: List<FilterSection>,
)

@Serializable
class FilterSection(
    val name: String,
    val param: String,
    val options: List<FilterOption>,
)

@Serializable
class FilterOption(
    val label: String,
    val value: String,
)
