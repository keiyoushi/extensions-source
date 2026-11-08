package eu.kanade.tachiyomi.extension.en.streamexunoriginal

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters() = FilterList(
    Filter.Header("The list is ignored when searching by title"),
    ListFilter(),
)

class ListFilter : Filter.Select<String>("List", LISTS.map { it.first }.toTypedArray()) {
    val value get() = LISTS[state].second
}

private val LISTS = listOf(
    "Most popular" to "popular",
    "Trending" to "trending",
    "Top rated" to "top-rated",
)
