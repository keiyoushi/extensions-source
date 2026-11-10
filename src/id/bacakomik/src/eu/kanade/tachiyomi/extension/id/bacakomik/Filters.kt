package eu.kanade.tachiyomi.extension.id.bacakomik

import eu.kanade.tachiyomi.source.model.Filter

class AuthorFilter : Filter.Text("Author")

class YearFilter : Filter.Text("Year")

class TypeFilter :
    UriPartFilter(
        "Type",
        arrayOf(
            Pair("Default", ""),
            Pair("Manga", "Manga"),
            Pair("Manhwa", "Manhwa"),
            Pair("Manhua", "Manhua"),
            Pair("Comic", "Comic"),
        ),
    )

class SortByFilter :
    UriPartFilter(
        "Sort By",
        arrayOf(
            Pair("Default", ""),
            Pair("A-Z", "title"),
            Pair("Z-A", "titlereverse"),
            Pair("Latest Update", "update"),
            Pair("Latest Added", "latest"),
            Pair("Popular", "popular"),
        ),
    )

class StatusFilter :
    UriPartFilter(
        "Status",
        arrayOf(
            Pair("All", ""),
            Pair("Ongoing", "ongoing"),
            Pair("Completed", "completed"),
            Pair("Hiatus", "hiatus"),
        ),
    )

class Genre(name: String, val id: String = name) : Filter.CheckBox(name)
class GenreListFilter(genres: List<Genre>) : Filter.Group<Genre>("Genre", genres)

open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}
