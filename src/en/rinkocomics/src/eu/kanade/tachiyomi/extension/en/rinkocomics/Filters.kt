package eu.kanade.tachiyomi.extension.en.rinkocomics

import eu.kanade.tachiyomi.source.model.Filter

open class SelectFilter(name: String, private val options: List<Pair<String, String>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val value get() = options[state].second
}

class SortFilter :
    SelectFilter(
        "Sort by",
        listOf(
            "Most viewed" to "views",
            "Latest update" to "updated",
            "Newest series" to "new",
            "A-Z" to "az",
        ),
    )

class StatusFilter :
    SelectFilter(
        "Status",
        listOf(
            "Any" to "",
            "Ongoing" to "ongoing",
            "Completed" to "completed",
            "Season end" to "season_end",
            "Dropped" to "dropped",
        ),
    )

class Genre(name: String, val slug: String) : Filter.CheckBox(name)

class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)
