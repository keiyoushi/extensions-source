package eu.kanade.tachiyomi.extension.en.earlymanga

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

abstract class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
    defaultValue: String? = null,
) : Filter.Select<String>(
    name,
    options.map { it.first }.toTypedArray(),
    options.indexOfFirst { it.second == defaultValue }.takeIf { it != -1 } ?: 0,
) {
    val selected get() = options[state].second
}

class CheckBoxFilter(
    name: String,
    val value: String,
) : Filter.CheckBox(name)

abstract class CheckBoxFilterGroup(
    name: String,
    options: List<Pair<String, String>>,
) : Filter.Group<CheckBoxFilter>(
    name,
    options.map { CheckBoxFilter(it.first, it.second) },
) {
    val checked get() = state.filter { it.state }.map { it.value }
}

class TriStateFilter(
    name: String,
    val value: String,
) : Filter.TriState(name)

class TriStateFilterGroup(
    name: String,
    options: List<Pair<String, String>>,
) : Filter.Group<TriStateFilter>(
    name,
    options.map { TriStateFilter(it.first, it.second) },
) {
    val included get() = state.filter { it.isIncluded() }.map { it.value }
    val excluded get() = state.filter { it.isExcluded() }.map { it.value }
}

class OrderByFilter(
    default: String? = null,
) : SelectFilter("Order by", options.map { Pair(it, it) }, default) {
    companion object {
        private val options = listOf(
            "Views",
            "Bookmarks",
            "Number of chapters",
            "Rating",
        )

        val POPULAR = FilterList(OrderByFilter("Views"))
    }
}

class SortFilter : SelectFilter("Sort By", options) {
    companion object {
        private val options = listOf(
            Pair("Descending", "desc"),
            Pair("Ascending", "asc"),
        )
    }
}

class TypeFilter : CheckBoxFilterGroup("Type", options) {
    companion object {
        private val options = listOf(
            Pair("Manga", "Japanese"),
            Pair("Manhwa", "Korean"),
            Pair("Manhua", "Chinese"),
            Pair("Comic", "English"),
        )
    }
}

class StatusFilter : CheckBoxFilterGroup("Status", options.map { Pair(it, it) }) {
    companion object {
        private val options = listOf(
            "Ongoing",
            "Completed",
            "Cancelled",
            "Hiatus",
        )
    }
}
