package eu.kanade.tachiyomi.extension.id.manhwaindo

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(displayName: String, private val vals: List<Pair<String, String>>, state: Int = 0) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), state) {
    fun toUriPart() = vals[state].second
}

class SortFilter(state: Int = 0) :
    UriPartFilter(
        "Sort",
        listOf(
            "Most read" to "views",
            "Latest update" to "modified",
            "Newest" to "date",
            "Title A-Z" to "title",
        ),
        state,
    )

class StatusFilter :
    UriPartFilter(
        "Status",
        listOf(
            "All" to "",
            "Ongoing" to "Ongoing",
            "Completed" to "Completed",
            "Hiatus" to "Hiatus",
        ),
    )

class TypeFilter :
    UriPartFilter(
        "Type",
        listOf(
            "All" to "",
            "Manga" to "Manga",
            "Manhua" to "Manhua",
            "Manhwa" to "Manhwa",
            "Comic" to "Comic",
            "Novel" to "Novel",
        ),
    )

class Genre(name: String, val value: String) : Filter.CheckBox(name)

class GenreFilter(genres: List<GenreDto>) : Filter.Group<Genre>("Genre", genres.map { Genre(it.name, it.value) })
