package eu.kanade.tachiyomi.extension.all.nakedpics

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter : Filter.Select<String>("Sort", arrayOf("Most popular", "Newest")) {
    val newest get() = state == 1
}

class CategoryFilter(private val options: List<CategoryOption>) : Filter.Select<String>("Category", arrayOf("Any") + options.map { it.name }) {
    val selected get() = options.getOrNull(state - 1)?.path
}
