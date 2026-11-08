package eu.kanade.tachiyomi.extension.en.toonhub

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters() = FilterList(
    GenreFilter(),
)

class GenreFilter : Filter.Select<String>("Genre", GENRES.map { it.first }.toTypedArray()) {
    val value get() = GENRES[state].second
}

private val GENRES = listOf(
    "All" to null,
    "Adventure" to "adventure",
    "Business" to "business",
    "Cheating" to "cheating",
    "Comedy" to "comedy",
    "Drama" to "drama",
    "Exclusive" to "exclusive",
    "Family" to "family",
    "Fantasy" to "fantasy",
    "Harem" to "harem",
    "Historical" to "historical",
    "Mystery" to "mystery",
    "Psychological" to "psychological",
    "Romance" to "romance",
    "School Life" to "school-life",
    "Sci-Fi" to "sci-fi",
    "Sport" to "sport",
)
