package eu.kanade.tachiyomi.extension.en.webdexscans

import eu.kanade.tachiyomi.source.model.Filter

open class SelectFilter(name: String, private val options: Array<Pair<String, String>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val selected: String?
        get() = options[state].second.takeIf { it.isNotEmpty() }
}

class GenreFilter :
    SelectFilter(
        "Genre",
        arrayOf(
            "All Genres" to "",
            "Action" to "action",
            "Adventure" to "adventure",
            "Comedy" to "comedy",
            "Drama" to "drama",
            "Fantasy" to "fantasy",
            "Isekai" to "isekai",
            "Martial Arts" to "martial-arts",
            "Mystery" to "mystery",
            "Romance" to "romance",
            "Sci-Fi" to "sci-fi",
            "Seinen" to "seinen",
            "Shounen" to "shounen",
            "Slice of Life" to "slice-of-life",
            "Supernatural" to "supernatural",
        ),
    )

class TypeFilter :
    SelectFilter(
        "Type",
        arrayOf(
            "All Types" to "",
            "Manhwa" to "manhwa",
            "Manga" to "manga",
            "Manhua" to "manhua",
            "Webtoon" to "webtoon",
        ),
    )

class StatusFilter :
    SelectFilter(
        "Status",
        arrayOf(
            "All Status" to "",
            "Ongoing" to "ongoing",
            "Completed" to "completed",
            "Hiatus" to "hiatus",
        ),
    )

class SortFilter :
    SelectFilter(
        "Sort By",
        arrayOf(
            "Latest Update" to "latest",
            "Most Popular" to "popular",
            "Highest Rating" to "rating",
            "Alphabetical" to "a-z",
        ),
    )
