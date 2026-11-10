package eu.kanade.tachiyomi.extension.en.manhwax

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter : Filter.Select<String>("Sort", SORTS.map { it.first }.toTypedArray()) {
    val value get() = SORTS[state].second
}

class GenreFilter(private val genres: List<GenreDto>) : Filter.Select<String>("Genre", arrayOf("All") + genres.map { it.name }) {
    val slug get() = genres.getOrNull(state - 1)?.slug
}

private val SORTS = listOf(
    "Popular" to "popular",
    "Latest" to null,
    "Top Rated" to "rating",
)
