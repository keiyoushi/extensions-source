package eu.kanade.tachiyomi.extension.en.toonz

import eu.kanade.tachiyomi.source.model.Filter

class CatalogFilter(
    private val options: List<Pair<String, String>> = listOf(
        "All" to "comics",
        "Manhwa" to "manhwa/browse",
        "Manga" to "manga/browse",
        "Western" to "western/browse",
        "Adult" to "adult/browse",
    ),
) : Filter.Select<String>("Catalog", options.map { it.first }.toTypedArray()) {
    fun getValue(): String = options[state].second
}

class SortFilter(
    private val options: List<Pair<String, String>> = listOf(
        "Popular" to "popular",
        "Latest updates" to "latest",
    ),
) : Filter.Select<String>("Sort", options.map { it.first }.toTypedArray()) {
    fun getValue(): String = options[state].second
}

class GenreFilter(
    private val options: List<Pair<String, String>>,
) : Filter.Select<String>("Genre", options.map { it.first }.toTypedArray()) {
    fun getValue(): String = options[state].second
}

val defaultGenres = listOf(
    "None" to "",
    "Action" to "action",
    "Adult" to "adult",
    "Adventure" to "adventure",
    "Boys Love" to "boys-love",
    "Comedy" to "comedy",
    "Drama" to "drama",
    "Ecchi" to "ecchi",
    "Fantasy" to "fantasy",
    "Harem" to "harem",
    "Hentai" to "hentai",
    "Historical" to "historical",
    "Horror" to "horror",
    "Isekai" to "isekai",
    "Josei" to "josei",
    "Manhua" to "manhua",
    "Manhwa" to "manhwa",
    "Martial Arts" to "martial-arts",
    "Mature" to "mature",
    "Mystery" to "mystery",
    "Psychological" to "psychological",
    "Romance" to "romance",
    "School Life" to "school-life",
    "Sci-Fi" to "sci-fi",
    "Seinen" to "seinen",
    "Shoujo" to "shoujo",
    "Shounen" to "shounen",
    "Slice of Life" to "slice-of-life",
    "Smut" to "smut",
    "Supernatural" to "supernatural",
    "Tragedy" to "tragedy",
    "Webtoons" to "webtoons",
    "Yaoi" to "yaoi",
    "Yuri" to "yuri",
)
