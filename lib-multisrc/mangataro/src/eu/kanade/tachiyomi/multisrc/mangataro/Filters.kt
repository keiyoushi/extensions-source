package eu.kanade.tachiyomi.multisrc.mangataro

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import java.util.Calendar

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

class SearchWithFilters : Filter.CheckBox("Apply filters to Text Search", false)

class TypeFilter :
    SelectFilter<String?>(
        name = "Type",
        options = listOf(
            "All" to null,
            "Manga" to "Manga",
            "Manhwa" to "Manhwa",
            "Manhua" to "Manhua",
        ),
    )

class StatusFilter :
    SelectFilter<String?>(
        name = "Status",
        options = listOf(
            "All" to null,
            "Completed" to "Completed",
            "Ongoing" to "Ongoing",
        ),
    )

class YearFilter :
    SelectFilter<Int?>(
        name = "Year",
        options = buildList {
            add("All" to null)
            val current = Calendar.getInstance().get(Calendar.YEAR)
            (current downTo 1949).mapTo(this) { it.toString() to it }
        },
    )

class TagFilter(options: List<Pair<String, Int>> = emptyList()) :
    CheckBoxGroup<Int>(
        name = "Tags",
        options = options,
    )

class TagFilterMatch :
    SelectFilter<String>(
        name = "Tag Match",
        options = listOf(
            "Any" to "any",
            "All" to "all",
        ),
    )

class SortFilter(
    state: Selection = Selection(0, false),
) : Filter.Sort(
    name = "Sort",
    values = sort.map { it.first }.toTypedArray(),
    state = state,
) {
    private val sortDirection
        get() = if (state?.ascending == true) {
            "asc"
        } else {
            "desc"
        }
    val selected get() = "${sort[state?.index ?: 0].second}_$sortDirection"

    companion object {
        val popular = FilterList(
            SortFilter(Selection(3, false)),
            TagFilterMatch(),
        )
        val latest = FilterList(
            SortFilter(Selection(0, false)),
            TagFilterMatch(),
        )
    }
}

private val sort = listOf(
    "Latest Updates" to "post",
    "Release Date" to "release",
    "Title A-Z" to "title",
    "Popular" to "popular",
)
