package eu.kanade.tachiyomi.extension.en.mangamelon

import eu.kanade.tachiyomi.source.model.Filter

private val SORT_OPTIONS = arrayOf("Latest", "Hot", "Top Rated", "New")
private val SORT_VALUES = arrayOf("latest", "popular", "rating", "newest")

class SortFilter : Filter.Sort("Sort", SORT_OPTIONS) {
    val value: String get() = SORT_VALUES[state?.index ?: 0]
}

// "All" is index 0 and maps to an empty genre string; the API filters by exact
// genre only in browse mode (it is ignored when a text search query is present).
private val GENRE_OPTIONS = arrayOf(
    "All",
    "Action",
    "Adult",
    "Adventure",
    "Comedy",
    "Drama",
    "Ecchi",
    "Fantasy",
    "Gender Bender",
    "Harem",
    "Hentai",
    "Historical",
    "Horror",
    "Isekai",
    "Josei",
    "Martial Arts",
    "Mature",
    "Mecha",
    "Mystery",
    "Psychological",
    "Romance",
    "School Life",
    "Sci-Fi",
    "Seinen",
    "Shoujo",
    "Shoujo Ai",
    "Shounen",
    "Shounen Ai",
    "Slice of Life",
    "Smut",
    "Sports",
    "Supernatural",
    "Tragedy",
    "Yaoi",
    "Yuri",
    "Doujinshi",
    "Manhua",
    "Manhwa",
    "Shotacon",
    "Wuxia",
    "Gore",
)

class GenreFilter : Filter.Select<String>("Genre", GENRE_OPTIONS) {
    val value: String get() = if (state == 0) "" else GENRE_OPTIONS[state]
}
