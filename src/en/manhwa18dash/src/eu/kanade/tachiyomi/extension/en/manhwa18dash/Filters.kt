package eu.kanade.tachiyomi.extension.en.manhwa18dash

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

private val SORTS = arrayOf(
    "Latest Release" to "latest",
    "Top Manhwa" to "popular",
    "New Manhwa" to "newest",
)

class SortFilter : Filter.Select<String>("Sort by", SORTS.map { it.first }.toTypedArray(), 1) {
    val value get() = SORTS[state].second
}

class StatusFilter : Filter.Select<String>("Status", arrayOf("All", "Ongoing", "Completed")) {
    val value get() = values[state].lowercase().takeIf { state > 0 }
}

class Genre(name: String) : Filter.CheckBox(name)

class GenreFilter(genres: List<String>) : Filter.Group<Genre>("Genres", genres.map(::Genre))

fun getFilters(genres: List<String>?): FilterList {
    val filters = mutableListOf<Filter<*>>(SortFilter(), StatusFilter())
    if (!genres.isNullOrEmpty()) {
        filters += Filter.Separator()
        filters += Filter.Header("Series with all of the selected genres")
        filters += GenreFilter(genres)
    }
    return FilterList(filters)
}

// The site's genre links use the lowercase name with dashes (/genre/part-time-job)
fun genreKey(name: String) = name.trim().lowercase().replace(' ', '-')
