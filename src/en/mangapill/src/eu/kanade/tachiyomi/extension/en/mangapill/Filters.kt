package eu.kanade.tachiyomi.extension.en.mangapill

import eu.kanade.tachiyomi.source.model.Filter

class Type :
    UriPartFilter(
        "Type",
        arrayOf(
            Pair("All", ""),
            Pair("Manga", "manga"),
            Pair("Novel", "novel"),
            Pair("One-Shot", "one-shot"),
            Pair("Doujinshi", "doujinshi"),
            Pair("Manhwa", "manhwa"),
            Pair("Manhua", "manhua"),
            Pair("Oel", "oel"),
        ),
    )

class Genre(name: String, val id: String = name) : Filter.TriState(name)

class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)

class Status :
    UriPartFilter(
        "Status",
        arrayOf(
            Pair("All", ""),
            Pair("Publishing", "publishing"),
            Pair("Finished", "finished"),
            Pair("On Hiatus", "on hiatus"),
            Pair("Discontinued", "discontinued"),
            Pair("Not yet Published", "not yet published"),
        ),
    )

open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

fun getGenreList() = listOf(
    Genre("Action"),
    Genre("Adventure"),
    Genre("Cars"),
    Genre("Comedy"),
    Genre("Dementia"),
    Genre("Demons"),
    Genre("Drama"),
    Genre("Ecchi"),
    Genre("Fantasy"),
    Genre("Game"),
    Genre("Harem"),
    Genre("Hentai"),
    Genre("Historical"),
    Genre("Horror"),
    Genre("Josei"),
    Genre("Kids"),
    Genre("Magic"),
    Genre("Martial Arts"),
    Genre("Mecha"),
    Genre("Military"),
    Genre("Music"),
    Genre("Mystery"),
    Genre("Parody"),
    Genre("Police"),
    Genre("Psychological"),
    Genre("Romance"),
    Genre("Samurai"),
    Genre("School"),
    Genre("Sci-Fi"),
    Genre("Seinen"),
    Genre("Shoujo"),
    Genre("Shoujo Ai"),
    Genre("Shounen"),
    Genre("Shounen Ai"),
    Genre("Slice of Life"),
    Genre("Space"),
    Genre("Sports"),
    Genre("Super Power"),
    Genre("Supernatural"),
    Genre("Thriller"),
    Genre("Vampire"),
    Genre("Yaoi"),
    Genre("Yuri"),
)
