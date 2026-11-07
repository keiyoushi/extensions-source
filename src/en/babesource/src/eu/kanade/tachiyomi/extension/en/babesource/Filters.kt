package eu.kanade.tachiyomi.extension.en.babesource

import eu.kanade.tachiyomi.source.model.Filter

enum class SortOption(val title: String, val globalPath: String, val listPath: String) {
    MOST_VIEWED("Most viewed", "most-viewed", "views"),
    NEWEST("Newest", "newest", "newest"),
    TOP_RATED("Top rated", "top-rated", "views"),
}

class SortFilter : Filter.Select<String>("Sort (Top rated: all galleries only)", SortOption.entries.map { it.title }.toTypedArray()) {
    val selected get() = SortOption.entries[state]
}

open class ListFilter(name: String, private val options: List<ListOption>) : Filter.Select<String>(name, arrayOf("Any") + options.map { it.name }) {
    val selected get() = options.getOrNull(state - 1)?.path
}

class CategoryFilter(options: List<ListOption>) : ListFilter("Category", options)

class PaysiteFilter(options: List<ListOption>) : ListFilter("Paysite", options)

class TagFilter(options: List<ListOption>) : ListFilter("Tag", options)
