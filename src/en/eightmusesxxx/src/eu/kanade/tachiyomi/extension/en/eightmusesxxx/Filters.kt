package eu.kanade.tachiyomi.extension.en.eightmusesxxx

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(categories: List<String>?) = if (categories == null) {
    FilterList()
} else {
    FilterList(
        Filter.Header("Ignored with a text search"),
        CategoryFilter(categories),
    )
}

class CategoryFilter(private val slugs: List<String>) : Filter.Select<String>("Category", (listOf("All") + slugs.map { it.replace('-', ' ') }).toTypedArray()) {
    val slug get() = slugs.getOrNull(state - 1)
}
