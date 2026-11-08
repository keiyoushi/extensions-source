package eu.kanade.tachiyomi.extension.all.cinblue

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters() = FilterList(
    Filter.Header("Tags can be searched with tag:\"name\", artist:\"name\", parody:\"name\"... and left out with -tag:\"name\""),
    SortFilter(),
)

class SortFilter : Filter.Select<String>("Sort by", SORTS.map { it.first }.toTypedArray()) {
    val value get() = SORTS[state].second
}

private val SORTS = listOf(
    "Popular: today" to "popular-today",
    "Popular: this week" to "popular-weekly",
    "Popular: all time" to "popular",
    "Recent" to "date",
)
