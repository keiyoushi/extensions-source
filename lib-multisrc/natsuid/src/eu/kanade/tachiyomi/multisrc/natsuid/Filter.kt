package eu.kanade.tachiyomi.multisrc.natsuid

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

abstract class SelectFilter<T>(
    name: String,
    private val options: List<Pair<String, T>>,
) : Filter.Select<String>(
    name,
    options.map { it.first }.toTypedArray(),
) {
    val selected get() = options[state].second
}

class CheckBoxFilter<T>(name: String, val value: T) : Filter.CheckBox(name)

abstract class CheckBoxGroup<T>(
    name: String,
    options: List<Pair<String, T>>,
) : Filter.Group<CheckBoxFilter<T>>(
    name,
    options.map { CheckBoxFilter(it.first, it.second) },
) {
    val checked get() = state.filter { it.state }.map { it.value }
}

class TriStateFilter<T>(name: String, val value: T) : Filter.TriState(name)

abstract class TriStateGroupFilter<T>(
    name: String,
    options: List<Pair<String, T>>,
) : Filter.Group<TriStateFilter<T>>(
    name,
    options.map { TriStateFilter(it.first, it.second) },
) {
    val included get() = state.filter { it.isIncluded() }.map { it.value }
    val excluded get() = state.filter { it.isExcluded() }.map { it.value }
}

class SortFilter(
    selection: Int = 0,
) : Filter.Sort(
    name = "Sort",
    values = sortBy.map { it.first }.toTypedArray(),
    state = Selection(selection, false),
) {
    val sort get() = sortBy[state?.index ?: 0].second
    val isAscending get() = state?.ascending ?: false

    companion object {
        private val sortBy = listOf(
            "Popular" to "popular",
            "Rating" to "rating",
            "Updated" to "updated",
            "Bookmarked" to "bookmarked",
            "Title" to "title",
        )

        val popular = FilterList(SortFilter(0))
        val latest = FilterList(SortFilter(2))
    }
}

class GenreFilter(
    genres: List<Pair<String, String>>,
) : TriStateGroupFilter<String>("Genre", genres)

class GenreInclusion : SelectFilter<String>(
    name = "Genre Inclusion Mode",
    options = listOf(
        "OR" to "OR",
        "AND" to "AND",
    ),
)

class GenreExclusion : SelectFilter<String>(
    name = "Genre Exclusion Mode",
    options = listOf(
        "OR" to "OR",
        "AND" to "AND",
    ),
)

class TypeFilter : CheckBoxGroup<String>(
    name = "Type",
    options = listOf(
        "Manga" to "manga",
        "Manhwa" to "manhwa",
        "Manhua" to "manhua",
    ),
)

class StatusFilter : CheckBoxGroup<String>(
    name = "Status",
    options = listOf(
        "Ongoing" to "ongoing",
        "Completed" to "completed",
        "Cancelled" to "cancelled",
        "On Hiatus" to "on-hiatus",
        "Unknown" to "unknown",
    ),
)
