package eu.kanade.tachiyomi.extension.all.mangadna

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(): FilterList = FilterList(
    Filter.Header("Filters are ignored when searching by query."),
    GenreFilter("Genre", GENRES_LIST),
    SortFilter("Sort by", SORTS_LIST),
)

internal class GenreFilter(name: String, genres: List<Pair<String, String>>) : SelectFilter(name, genres)
internal class SortFilter(name: String, sorts: List<Pair<String, String>>) : SelectFilter(name, sorts)

internal open class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val selected: String get() = options[state].second
}

private val GENRES_LIST = listOf(
    "Any" to "",
    "Action" to "action",
    "Adult" to "adult",
    "Adventure" to "adventure",
    "Comedy" to "comedy",
    "Cooking" to "cooking",
    "Crime" to "crime",
    "Drama" to "drama",
    "Fantasy" to "fantasy",
    "Harem" to "harem",
    "Historical" to "historical",
    "Isekai" to "isekai",
    "Magic" to "magic",
    "Magical" to "magical",
    "Manhua" to "manhua",
    "Manhwa" to "manhwa",
    "Martial Arts" to "martial-arts",
    "Mature" to "mature",
    "Mystery" to "mystery",
    "Romance" to "romance",
    "School Life" to "school-life",
    "Sci-fi" to "sci-fi",
    "Shounen" to "shounen",
    "Shounen Ai" to "shounen-ai",
    "Slice of Life" to "slice-of-life",
    "Supernatural" to "supernatural",
    "Thriller" to "thriller",
    "Uncensored" to "uncensored",
)

private val SORTS_LIST = listOf(
    "Latest" to "latest",
    "A-Z" to "alphabet",
    "Rating" to "rating",
    "Trending" to "trending",
)
