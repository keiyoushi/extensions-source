package eu.kanade.tachiyomi.extension.all.yaoime

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

class TriStateOption(name: String) : Filter.TriState(name)

open class TriStateGroup(name: String, values: List<String>) : Filter.Group<TriStateOption>(name, values.map(::TriStateOption)) {
    val included get() = state.filter { it.isIncluded() }.map { it.name }
    val excluded get() = state.filter { it.isExcluded() }.map { it.name }
}

class GenreFilter(values: List<String>) : TriStateGroup("Genres", values)

class TagFilter(values: List<String>) : TriStateGroup("Tags", values)

open class SelectFilter(name: String, private val options: List<String>) : Filter.Select<String>(name, (listOf("All") + options.map { it.replaceFirstChar(Char::titlecase) }).toTypedArray()) {
    val selected get() = options.getOrNull(state - 1)
}

class TypeFilter(values: List<String>) : SelectFilter("Type", values)

class StatusFilter(values: List<String>) : SelectFilter("Status", values)

class ContentFilter(values: List<String>) : SelectFilter("Content rating", values)

class DemographicFilter(values: List<String>) : SelectFilter("Demographic", values)

class SortFilter(state: Selection = Selection(2, false)) : Filter.Sort("Sort by", SORTS.map { it.first }.toTypedArray(), state) {
    val sort get() = SORTS[state!!.index].second
    val dir get() = if (state!!.ascending) "asc" else "desc"

    companion object {
        private val SORTS = listOf(
            "Update time" to "update",
            "Newest added" to "create",
            "Popularity" to "popular",
            "Rating" to "rating",
            "Chapters" to "chapters",
            "Title" to "az",
        )

        val LATEST = FilterList(SortFilter(Selection(0, false)))
        val POPULAR = FilterList(SortFilter(Selection(2, false)))
    }
}
