package eu.kanade.tachiyomi.extension.id.keikomik

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(): FilterList = FilterList(
    Filter.Header("Filters are ignored in a text search"),
    SortFilter(),
    GenreFilter(),
)

class SortFilter : Filter.Select<String>("Sort", arrayOf("Popular", "Latest update")) {
    val field get() = if (state == 0) "views" else "UpdateAt"
}

class GenreFilter : Filter.Select<String>("Genre", (listOf("All") + GENRES).toTypedArray()) {
    val value get() = GENRES.getOrNull(state - 1)
}

// The genres the site's list offers
private val GENRES = listOf(
    "Action",
    "Fantasy",
    "Adventure",
    "Comedy",
    "Sci-Fi",
    "Romance",
    "Mystery",
    "Horror",
    "Slice of Life",
    "Supernatural",
    "Isekai",
)
