package eu.kanade.tachiyomi.extension.th.oremanga

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(
    displayName: String,
    private val vals: Array<Pair<String, String>>,
) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

class AuthorFilter : Filter.Text("Author")
class YearFilter : Filter.Text("Year")

class StatusFilter :
    UriPartFilter(
        "Status",
        arrayOf(
            Pair("All", ""),
            Pair("Ongoing", "ongoing"),
            Pair("Completed", "completed"),
        ),
    )

class TypeFilter :
    UriPartFilter(
        "Type",
        arrayOf(
            Pair("All", ""),
            Pair("Manga", "Manga"),
            Pair("Manhua", "Manhua"),
            Pair("Manhwa", "Manhwa"),
            Pair("One-shot", "One-shot"),
            Pair("Doujinshi", "Doujinshi"),
        ),
    )

class OrderFilter :
    UriPartFilter(
        "Order by",
        arrayOf(
            Pair("Latest Update", "update"),
            Pair("A-Z", "title"),
            Pair("Z-A", "titlereverse"),
            Pair("Latest Added", "latest"),
            Pair("Popular", "popular"),
            Pair("Rating", "rating"),
        ),
    )

class Genre(name: String, val id: String) : Filter.CheckBox(name)
class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Genre", genres)

fun getGenreList() = listOf(
    Genre("Action", "action"),
    Genre("Adult", "adult"),
    Genre("Adventure", "adventure"),
    Genre("Comedy", "comedy"),
    Genre("Drama", "drama"),
    Genre("Ecchi", "ecchi"),
    Genre("Fantasy", "fantasy"),
    Genre("Harem", "harem"),
    Genre("Historical", "historical"),
    Genre("Horror", "horror"),
    Genre("Isekai", "isekai"),
    Genre("Josei", "josei"),
    Genre("Martial Arts", "martial-arts"),
    Genre("Mature", "mature"),
    Genre("Mecha", "mecha"),
    Genre("Mystery", "mystery"),
    Genre("Romance", "romance"),
    Genre("School Life", "school-life"),
    Genre("Sci-fi", "sci-fi"),
    Genre("Seinen", "seinen"),
    Genre("Shoujo", "shoujo"),
    Genre("Shounen", "shounen"),
    Genre("Slice of Life", "slice-of-life"),
    Genre("Smut", "smut"),
    Genre("Sports", "sports"),
    Genre("Supernatural", "supernatural"),
    Genre("Tragedy", "tragedy"),
    Genre("Yuri", "yuri"),
)
