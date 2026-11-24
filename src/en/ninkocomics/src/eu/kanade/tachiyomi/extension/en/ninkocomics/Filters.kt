package eu.kanade.tachiyomi.extension.en.ninkocomics

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(): FilterList = FilterList(
    Filter.Header("Filters are ignored when searching by text"),
    SortFilter(),
    GenreFilter(),
    StatusFilter(),
)

open class SelectFilter(name: String, private val options: List<Pair<String, String>>, state: Int = 0) : Filter.Select<String>(name, options.map { it.first }.toTypedArray(), state) {
    val value get() = options[state].second
}

class SortFilter :
    SelectFilter(
        "Sort",
        listOf(
            "Latest update" to "latest",
            "Most read" to "popular",
            "Newest" to "new",
            "A-Z" to "title",
        ),
        1,
    )

class GenreFilter :
    SelectFilter(
        "Genre",
        listOf(
            "All genres" to "",
            "Action" to "action",
            "Adventure" to "adventure",
            "Comedy" to "comedy",
            "Crime" to "crime",
            "Drama" to "drama",
            "Fantasy" to "fantasy",
            "Harem" to "harem",
            "Historical" to "historical",
            "Horror" to "horror",
            "Isekai" to "isekai",
            "Martial Arts" to "martial-arts",
            "Mature" to "mature",
            "Murim" to "murim",
            "Mystery" to "mystery",
            "Sci-Fi" to "sci-fi",
            "Slice of Life" to "slice-of-life",
            "Sports" to "sports",
            "Superhero" to "superhero",
            "Tragedy" to "tragedy",
            "Wuxia" to "wuxia",
        ),
    )

class StatusFilter :
    SelectFilter(
        "Status",
        listOf(
            "Any status" to "",
            "Ongoing" to "ongoing",
            "Completed" to "completed",
            "Hiatus" to "hiatus",
            "Dropped" to "dropped",
        ),
    )
