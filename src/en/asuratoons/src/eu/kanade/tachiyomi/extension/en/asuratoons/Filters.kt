package eu.kanade.tachiyomi.extension.en.asuratoons

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters() = FilterList(
    Filter.Header("Filters are ignored with a text search"),
    SortFilter(),
    StatusFilter(),
    GenreFilter(),
)

open class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val value get() = options[state].second
}

class SortFilter :
    SelectFilter(
        "Sort by",
        listOf(
            "Most popular" to "popular",
            "Latest updated" to "latest",
            "Newest added" to "newest",
            "Highest rated" to "rating",
            "A to Z" to "alphabetical",
            "Most chapters" to "chapters",
        ),
    )

class StatusFilter :
    SelectFilter(
        "Status",
        listOf(
            "All" to "",
            "Ongoing" to "Ongoing",
            "Completed" to "Completed",
            "Cancelled" to "cancelled",
        ),
    )

class GenreFilter :
    SelectFilter(
        "Genre",
        listOf("All" to "") + listOf(
            "Action", "Adventure", "Boys' Love", "Comedy", "Drama", "Erotica", "Fantasy", "Girls' Love", "Harem",
            "Historical", "Horror", "Mystery", "Office Workers", "Psychological", "Raw", "Romance", "School Life",
            "Sports", "Supernatural", "Thriller",
        ).map { it to it },
    )
