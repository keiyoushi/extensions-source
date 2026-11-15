package eu.kanade.tachiyomi.extension.id.mynimeku

import eu.kanade.tachiyomi.source.model.Filter

class StatusFilter :
    UriPartFilter(
        "Status",
        arrayOf(
            Pair("All", ""),
            Pair("On-Going", "on-going"),
            Pair("Completed", "completed"),
        ),
    )

class OrderFilter :
    UriPartFilter(
        "Order by",
        arrayOf(
            Pair("Popular", "popular"),
            Pair("A-Z", "az"),
            Pair("Z-A", "za"),
            Pair("Modified", "modified"),
            Pair("Date", "date"),
        ),
    )

class Type(name: String, val id: String) : Filter.CheckBox(name)

class TypeFilter :
    Filter.Group<Type>(
        "Type",
        listOf(
            Type("Manga", "MANGA"),
            Type("Manhua", "MANHUA"),
            Type("Manhwa", "MANHWA"),
            Type("Doujinshi", "DOUJINSHI"),
            Type("One-Shot", "ONE-SHOT"),
        ),
    )

class Genre(name: String, val id: String) : Filter.CheckBox(name)

class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Genre", genres)

open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}
