package eu.kanade.tachiyomi.extension.en.duskscans

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter :
    Filter.Select<String>(
        "Sort by",
        arrayOf(
            "Latest Update",
            "New Releases",
            "Most Popular",
            "Top Rated",
            "A-Z",
        ),
    )

class CheckBoxItem(name: String) : Filter.CheckBox(name)

open class CheckBoxGroup(
    name: String,
    options: List<String>,
) : Filter.Group<CheckBoxItem>(
    name = name,
    state = options.map(::CheckBoxItem),
) {
    val checked get() = state.filter { it.state }.map { it.name }
}

class StatusFilter(options: List<String>) : CheckBoxGroup("Status", options)

class TypeFilter(options: List<String>) : CheckBoxGroup("Type", options)

class TriStateItem(name: String) : Filter.TriState(name)

class GenreFilter(genres: List<String>) :
    Filter.Group<TriStateItem>(
        name = "Genre",
        state = genres.map(::TriStateItem),
    ) {
    val included get() = state.filter { it.isIncluded() }.map { it.name }
    val excluded get() = state.filter { it.isExcluded() }.map { it.name }
}
