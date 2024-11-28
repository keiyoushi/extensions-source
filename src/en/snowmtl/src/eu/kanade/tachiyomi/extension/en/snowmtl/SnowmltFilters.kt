package eu.kanade.tachiyomi.extension.en.snowmtl

import eu.kanade.tachiyomi.source.model.Filter

class SelectionList(displayName: String, private val vals: List<Option>, state: Int = 0) :
    Filter.Select<String>(displayName, vals.map { it.name }.toTypedArray(), state) {
    fun selected() = vals[state]
}

data class Option(val name: String = "", val value: String = "", val query: String = "")

class GenreList(title: String, genres: List<Genre>) :
    Filter.Group<GenreCheckBox>(title, genres.map { GenreCheckBox(it.name, it.id) })

class GenreCheckBox(name: String, val id: String = name) : Filter.CheckBox(name)

class Genre(val name: String, val id: String = name)

val genreList: List<Genre> = listOf(
    Genre("Action"),
    Genre("Adult"),
    Genre("Adventure"),
    Genre("Comedy"),
    Genre("Drama"),
    Genre("Ecchi"),
    Genre("Fantasy"),
    Genre("Gender Bender"),
    Genre("Harem"),
    Genre("Historical"),
    Genre("Horror"),
    Genre("Josei"),
    Genre("Lolicon"),
    Genre("Martial Arts"),
    Genre("Mature"),
    Genre("Mecha"),
    Genre("Mystery"),
    Genre("Psychological"),
    Genre("Romance"),
    Genre("School Life"),
    Genre("Sci-fi"),
    Genre("Seinen"),
    Genre("Shoujo"),
    Genre("Shoujo Ai"),
    Genre("Shounen"),
    Genre("Shounen Ai"),
    Genre("Slice of Life"),
    Genre("Smut"),
    Genre("Sports"),
    Genre("Supernatural"),
    Genre("Tragedy"),
    Genre("Yaoi"),
    Genre("Yuri"),
)

val sortByList = listOf(
    Option("All"),
    Option("Most Views", "views"),
    Option("Most Recent", "recent"),
).map { it.copy(query = "sort_by") }
