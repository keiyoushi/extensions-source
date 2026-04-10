package eu.kanade.tachiyomi.extension.id.komikucc

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

class SortFilter(
    state: String? = sortValues[0].second,
) : Filter.Select<String?>(
    name = "Sort",
    values = sortValues.map { it.first }.toTypedArray(),
    state = sortValues.indexOfFirst { it.second == state }.takeIf { it != -1 } ?: 0,
) {
    val sort get() = sortValues[state].second

    companion object {
        val popular get() = FilterList(SortFilter("popular"))
        val latest get() = FilterList(SortFilter("update"))
    }
}

private val sortValues = listOf(
    "Semua" to null,
    "A-Z" to "title",
    "Z-A" to "titlereverse",
    "Update" to "update",
    "New" to "latest",
    "Popular" to "popular",
)

class StatusFilter :
    Filter.Select<String?>(
        name = "Status",
        values = statusValues.map { it.first }.toTypedArray(),
    ) {
    val status get() = statusValues[state].second
}

private val statusValues = listOf(
    "Semua" to null,
    "Ongoing" to "ongoing",
    "Selesai" to "completed",
    "Hiatus" to "hiatus",
)

class TypeFilter :
    Filter.Select<String?>(
        name = "Type",
        values = typeValues.map { it.first }.toTypedArray(),
    ) {
    val type get() = typeValues[state].second
}

private val typeValues = listOf(
    "Semua" to null,
    "Manga" to "manga",
    "Manhwa" to "manhwa",
    "Manhua" to "manhua",
)

class CheckBoxFilter(name: String, val value: String) : Filter.CheckBox(name)

class GenreFilter(
    val genres: List<Genre>,
) : Filter.Group<CheckBoxFilter>(
    name = "Genres",
    state = genres.map { CheckBoxFilter(it.title, it.link) },
) {
    val checked get() = state.filter { it.state }.map { it.value }
}
