package eu.kanade.tachiyomi.multisrc.zmanga

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

class ProjectFilter :
    UriPartFilter(
        "Filter Project",
        arrayOf(
            Pair("Show all manga", ""),
            Pair("Show only project manga", "project-filter-on"),
        ),
    )

class AuthorFilter : Filter.Text("Author")

class YearFilter : Filter.Text("Year")

class StatusFilter : Filter.TriState("Completed")

class TypeFilter :
    UriPartFilter(
        "Type",
        arrayOf(
            Pair("All", ""),
            Pair("Manga", "Manga"),
            Pair("Manhua", "Manhua"),
            Pair("Manhwa", "Manhwa"),
            Pair("One-Shot", "One-Shot"),
            Pair("Doujin", "Doujin"),
        ),
    )

class OrderByFilter :
    UriPartFilter(
        "Order By",
        arrayOf(
            Pair("<select>", ""),
            Pair("A-Z", "title"),
            Pair("Z-A", "titlereverse"),
            Pair("Latest Update", "update"),
            Pair("Latest Added", "latest"),
            Pair("Popular", "popular"),
            Pair("Rating", "rating"),
        ),
    )

class Tag(val id: String, name: String) : Filter.CheckBox(name)

class GenreList(genres: List<Tag>) : Filter.Group<Tag>("Genres", genres)
