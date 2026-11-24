package eu.kanade.tachiyomi.extension.en.kaliscansorg

import eu.kanade.tachiyomi.source.model.Filter

class GenreCheckBox(name: String, val value: String) : Filter.CheckBox(name)

class GenreFilter(genres: List<Genre>) : Filter.Group<GenreCheckBox>("Genres", genres.map { GenreCheckBox(it.name, it.value) })

open class SelectFilter(name: String, private val options: List<Pair<String, String>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val selected get() = options[state].second
}

class StatusFilter :
    SelectFilter(
        "Status",
        listOf(
            "All" to "all",
            "On-going" to "ongoing",
            "Completed" to "completed",
        ),
    )

// "Created" sorts like "Updated" and "Rating" like "Views" on the site
class SortFilter :
    SelectFilter(
        "Order by",
        listOf(
            "Views" to "views",
            "Updated" to "updated_at",
            "Name A-Z" to "name",
        ),
    )
