package eu.kanade.tachiyomi.extension.en.mangafreak

import eu.kanade.tachiyomi.source.model.Filter

class Genre(name: String) : Filter.TriState(name)

class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)

fun getGenreList() = listOf(
    Genre("Act"),
    Genre("Adult"),
    Genre("Adventure"),
    Genre("Ancients"),
    Genre("Animated"),
    Genre("Comedy"),
    Genre("Demons"),
    Genre("Drama"),
    Genre("Ecchi"),
    Genre("Fantasy"),
    Genre("Gender Bender"),
    Genre("Harem"),
    Genre("Horror"),
    Genre("Josei"),
    Genre("Magic"),
    Genre("Martial Arts"),
    Genre("Mature"),
    Genre("Mecha"),
    Genre("Military"),
    Genre("Mystery"),
    Genre("One Shot"),
    Genre("Psychological"),
    Genre("Romance"),
    Genre("School Life"),
    Genre("Sci Fi"),
    Genre("Seinen"),
    Genre("Shoujo"),
    Genre("Shoujoai"),
    Genre("Shounen"),
    Genre("Shounenai"),
    Genre("Slice Of Life"),
    Genre("Smut"),
    Genre("Sports"),
    Genre("Super Power"),
    Genre("Supernatural"),
    Genre("Tragedy"),
    Genre("Vampire"),
    Genre("Yaoi"),
    Genre("Yuri"),
)

class TypeFilter :
    UriPartFilter(
        "Manga Type",
        arrayOf(
            Pair("Both", "0"),
            Pair("Manga", "2"),
            Pair("Manhwa", "1"),
        ),
    )

class StatusFilter :
    UriPartFilter(
        "Manga Status",
        arrayOf(
            Pair("Both", "0"),
            Pair("Completed", "1"),
            Pair("Ongoing", "2"),
        ),
    )

open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}
