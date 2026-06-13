package eu.kanade.tachiyomi.extension.ja.klto9

import eu.kanade.tachiyomi.source.model.Filter

class Genre(name: String) : Filter.TriState(name)
class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)
class StatusFilter : Filter.Select<String>("Status", STATUS_VALUES.map { it.first }.toTypedArray())
class AuthorFilter : Filter.Text("Author")
class GroupFilter : Filter.Text("Scanlation Group")
class SortFilter : Filter.Sort("Sort by", SORT_VALUES.map { it.first }.toTypedArray(), Selection(1, false))

val STATUS_VALUES = arrayOf(
    Pair("Anything", ""),
    Pair("Translating", "2"),
    Pair("Complete", "1"),
    Pair("Pause", "3"),
)

val SORT_VALUES = arrayOf(
    Pair("Name", "name"),
    Pair("Most Views", "views"),
    Pair("Last update", "last_update"),
)

fun getGenresList() = listOf(
    Genre("Action"),
    Genre("Adult"),
    Genre("Adventure"),
    Genre("Anime"),
    Genre("Comedy"),
    Genre("Comic"),
    Genre("Doujinshi"),
    Genre("Drama"),
    Genre("Ecchi"),
    Genre("Fantasy"),
    Genre("Gender Bender"),
    Genre("Harem"),
    Genre("Historical"),
    Genre("Horror"),
    Genre("Josei"),
    Genre("Live action"),
    Genre("Magic"),
    Genre("Manhua"),
    Genre("Manhwa"),
    Genre("Martial Arts"),
    Genre("Mature"),
    Genre("Mecha"),
    Genre("Mystery"),
    Genre("One shot"),
    Genre("Psychological"),
    Genre("Romance"),
    Genre("School Life"),
    Genre("Sci-fi"),
    Genre("Seinen"),
    Genre("Shoujo"),
    Genre("Shoujo Ai"),
    Genre("Shounen"),
    Genre("Shounen Ai"),
    Genre("Slice of life"),
    Genre("Smut"),
    Genre("Soft Yaoi"),
    Genre("Soft Yuri"),
    Genre("Sports"),
    Genre("Supernatural"),
    Genre("Comic Magazine"),
    Genre("Tragedy"),
    Genre("Color Manga"),
    Genre("Manga scan"),
    Genre("VnComic"),
    Genre("Webtoon"),
)
