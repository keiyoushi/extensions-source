package eu.kanade.tachiyomi.extension.en.readcomicsonlinelol

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

open class SelectFilter(
    name: String,
    private val options: List<Pair<String, String?>>,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val selected get() = options[state].second
}

class SortFilter :
    SelectFilter(
        "Sort by",
        listOf(
            "Most popular" to "popular",
            "Recently updated" to "updated",
            "A-Z" to "az",
            "Most issues" to "issues",
        ),
    )

// Matched against the publisher name, like the site does
class PublisherFilter :
    SelectFilter(
        "Publisher",
        listOf(
            "All" to null,
            "Marvel" to "Marvel",
            "DC Comics" to "DC",
            "Image Comics" to "Image",
        ),
    )

class StatusFilter :
    SelectFilter(
        "Status",
        listOf(
            "All" to null,
            "Ongoing" to "ongoing",
            "Completed" to "completed",
            "Updated this week" to "week",
        ),
    )

class GenreFilter :
    SelectFilter(
        "Genre",
        listOf("All" to null) + listOf(
            "Action",
            "Adventure",
            "Comedy",
            "Crime",
            "Drama",
            "Fantasy",
            "Graphic Novels",
            "Historical",
            "Horror",
            "Mature",
            "Movies & TV",
            "Mystery",
            "Mythology",
            "Sci-Fi",
            "Slice of Life",
            "Superhero",
            "Supernatural",
        ).map { it to it },
    )

fun getFilters() = FilterList(
    SortFilter(),
    PublisherFilter(),
    StatusFilter(),
    GenreFilter(),
)
