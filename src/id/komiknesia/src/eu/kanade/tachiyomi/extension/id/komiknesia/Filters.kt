package eu.kanade.tachiyomi.extension.id.komiknesia

import eu.kanade.tachiyomi.source.model.Filter

class OrderFilter :
    UriPartFilter(
        "Order by",
        arrayOf(
            Pair("Update", ""),
            Pair("Added", "Added"),
            Pair("Popular", "Popular"),
            Pair("Title (A-Z)", "Az"),
            Pair("Title (Z-A)", "Za"),
        ),
    )

class StatusFilter :
    UriPartFilter(
        "Status",
        arrayOf(
            Pair("All", ""),
            Pair("Ongoing", "Ongoing"),
            Pair("Completed", "Completed"),
            Pair("Hiatus", "Hiatus"),
        ),
    )

class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)
class Genre(name: String, val id: String) : Filter.CheckBox(name)

open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}
