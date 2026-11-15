package eu.kanade.tachiyomi.extension.en.dreammanga

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(genres: List<GenreDto>?) = FilterList(
    buildList {
        add(Filter.Header("Filters are ignored when searching by title"))
        add(SortFilter())
        if (genres.isNullOrEmpty()) {
            add(Filter.Header("Reset the filters to load the genres"))
        } else {
            add(GenreFilter(genres))
        }
        add(YearFilter("Year from"))
        add(YearFilter("Year to"))
    },
)

class SortFilter : Filter.Sort("Sort by", SORTS.map { it.first }.toTypedArray(), Selection(3, false)) {
    val value get() = SORTS[state?.index ?: 3].second
    val direction get() = if (state?.ascending == true) "asc" else "desc"
}

class Genre(name: String, val id: Int) : Filter.TriState(name)

class GenreFilter(genres: List<GenreDto>) : Filter.Group<Genre>("Genres", genres.map { Genre(it.value.replace('_', ' '), it.id) }) {
    val included get() = state.filter { it.isIncluded() }.map { it.id }
    val excluded get() = state.filter { it.isExcluded() }.map { it.id }
}

class YearFilter(name: String) : Filter.Text(name) {
    val year get() = state.trim().toIntOrNull()
}

private val SORTS = listOf(
    "Date added" to "date",
    "Date updated" to "editdate",
    "Rating" to "rating",
    "Read" to "news_read",
    "Comments" to "comm_num",
    "Title" to "title",
    "Year" to "year",
)
