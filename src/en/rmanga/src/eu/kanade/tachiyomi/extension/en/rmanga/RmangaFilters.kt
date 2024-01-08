package eu.kanade.tachiyomi.extension.en.rmanga

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

class Genre(
    name: String,
    val id: String,
) : Filter.TriState(name)

internal class GenreFilter(name: String, genres: List<Genre>) :
    Filter.Group<Genre>(name, genres)

private val genreList = listOf(
    Genre("Action", "1"),
    Genre("Adventure", "23"),
    Genre("All Categories", "42"),
    Genre("Comedy", "12"),
    Genre("Cooking", "51"),
    Genre("Doujinshi", "26"),
    Genre("Drama", "9"),
    Genre("Ecchi", "2"),
    Genre("Fantasy", "3"),
    Genre("Gender Bender", "30"),
    Genre("Harem", "4"),
    Genre("Historical", "36"),
    Genre("Horror", "34"),
    Genre("Isekai", "44"),
    Genre("Josei", "17"),
    Genre("Lolicon", "39"),
    Genre("Magic", "48"),
    Genre("Manga", "5"),
    Genre("Manhua", "31"),
    Genre("Manhwa", "32"),
    Genre("Martial Arts", "22"),
    Genre("Mature", "50"),
    Genre("Mecha", "33"),
    Genre("Mind Game", "52"),
    Genre("Mystery", "13"),
    Genre("None", "41"),
    Genre("One shot", "16"),
    Genre("Psychological", "14"),
    Genre("Recarnation", "49"),
    Genre("Romance", "6"),
    Genre("School Life", "10"),
    Genre("Sci fi", "19"),
    Genre("Seinen", "24"),
    Genre("Shotacon", "38"),
    Genre("Shoujo", "8"),
    Genre("Shoujo Ai", "37"),
    Genre("Shounen", "7"),
    Genre("Shounen Ai", "35"),
    Genre("Slice of Life", "21"),
    Genre("Sports", "29"),
    Genre("Supernatural", "11"),
    Genre("Time Travel", "45"),
    Genre("Tragedy", "15"),
    Genre("Uncategorized", "43"),
    Genre("Yaoi", "28"),
    Genre("Yuri", "20"),
)

internal class TypeFilter(name: String, private val types: Array<String>) :
    Filter.Select<String>(name, types) {
    fun getValue() = types[state]
}

private val typeFilter: Array<String> = arrayOf(
    "All",
    "Japanese",
    "Korean",
    "Chinese",
)

internal class AuthorFilter(title: String) : Filter.Text(title)
internal class ArtistFilter(title: String) : Filter.Text(title)

internal class StatusFilter(name: String, private val status: Array<String>) :
    Filter.Select<String>(name, status) {
    fun getValue() = status[state]
}

private val statusFilter: Array<String> = arrayOf(
    "Both",
    "Ongoing",
    "Completed",
)

fun getFilters() = FilterList(
    TypeFilter("Type", typeFilter),
    AuthorFilter("Author"),
    ArtistFilter("Artist"),
    StatusFilter("Status", statusFilter),
    GenreFilter("Genres", genreList),
)
