package eu.kanade.tachiyomi.extension.en.rule34comic

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

class SortFilter : Filter.Select<String>("Sort", SORTS.map { it.first }.toTypedArray()) {
    val value get() = SORTS[state].second
}

fun getFilters() = FilterList(SortFilter())

// Popular first
private val SORTS = listOf(
    "Most Viewed" to "album_viewed",
    "Newest" to "post_date",
    "Top Rated" to "rating",
)
