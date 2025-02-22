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

class StatusFilter(
    name: String,
    status: List<Pair<String, String>>,
) : Filter.Group<CheckBoxFilter>(
    name,
    status.map { CheckBoxFilter(it.first, it.second) },
) {
    val checked get() = state.filter { it.state }.map { it.value }.takeUnless { it.isEmpty() }
}

class SortFilter(defaultSort: String? = null) : SelectFilter(
    "Sort By",
    listOf(
        Pair("Title", "title"),
        Pair("Popularity", "popularity"),
        Pair("Latest", "latest"),
    ),
    defaultSort,
)
