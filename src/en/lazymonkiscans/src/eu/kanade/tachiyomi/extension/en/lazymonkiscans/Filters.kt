package eu.kanade.tachiyomi.extension.en.lazymonkiscans

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

class GenreFilter : Filter.Select<String>("Genre", GENRES) {
    val selected: String? get() = GENRES[state].takeIf { state > 0 }
}

private val GENRES = arrayOf(
    "All",
    "Action",
    "Adventure",
    "Comedy",
    "Drama",
    "Fantasy",
    "Horror",
    "Isekai",
    "Romance",
    "Sci-Fi",
    "Slice of Life",
    "Thriller",
)

fun getFilters() = FilterList(GenreFilter())
