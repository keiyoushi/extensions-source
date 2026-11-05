package eu.kanade.tachiyomi.extension.en.renascans

import eu.kanade.tachiyomi.source.model.Filter

private val SORT_OPTIONS = listOf(
    "Latest update" to "newest",
    "Popular" to "popular",
    "A - Z" to "title",
    "Most chapters" to "chapters",
)

class SortFilter(default: String) :
    Filter.Select<String>(
        "Sort by",
        SORT_OPTIONS.map { it.first }.toTypedArray(),
        SORT_OPTIONS.indexOfFirst { it.second == default },
    ) {
    val value get() = SORT_OPTIONS[state].second
}

class GenreTriState(name: String) : Filter.TriState(name)

class GenreFilter(genres: List<String>) : Filter.Group<GenreTriState>("Genres", genres.map(::GenreTriState)) {
    val included get() = state.filter { it.isIncluded() }.map { it.name }
    val excluded get() = state.filter { it.isExcluded() }.map { it.name }
}

class TextQueryFilter(name: String, val param: String) : Filter.Text(name)
