package eu.kanade.tachiyomi.extension.en.infinityscans

import eu.kanade.tachiyomi.source.model.Filter

abstract class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
    defaultValue: String? = null,
) : Filter.Select<String>(
    name,
    options.map { it.first }.toTypedArray(),
    options.indexOfFirst { it.second == defaultValue }.takeIf { it != -1 } ?: 0,
) {
    val selected get() = options[state].second.takeUnless { it.isEmpty() }
}

class CheckBoxFilter(
    name: String,
    val value: String,
) : Filter.CheckBox(name)

class GenreFilter(
    name: String,
    genres: List<Pair<String, String>>,
) : Filter.Group<CheckBoxFilter>(
    name,
    genres.map { CheckBoxFilter(it.first, it.second) },
) {
    val checked get() = state.filter { it.state }.map { it.value }.takeUnless { it.isEmpty() }
}

class AuthorFilter(
    name: String,
    authors: List<Pair<String, String>>,
) : Filter.Group<CheckBoxFilter>(
    name,
    authors.map { CheckBoxFilter(it.first, it.second) },
) {
    val checked get() = state.filter { it.state }.map { it.value }.takeUnless { it.isEmpty() }
}

class StatusFilter :
    Filter.Group<CheckBoxFilter>(
        "Status",
        listOf(
            CheckBoxFilter("Ongoing", "1"),
            CheckBoxFilter("Completed", "4"),
            CheckBoxFilter("Hiatus", "2"),
            CheckBoxFilter("Cancelled", "3"),
        ),
    ) {
    val checked get() = state.filter { it.state }.map { it.value }
}

enum class SortType(val value: String) {
    Title(""),
    Latest("1"),
    Popularity("2"),
}

class SortFilter(defaultSort: String? = null) :
    SelectFilter(
        "Sort By",
        SortType.values().map { it.name to it.value },
        defaultSort,
    )
