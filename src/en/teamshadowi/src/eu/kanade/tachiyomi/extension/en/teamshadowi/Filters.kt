package eu.kanade.tachiyomi.extension.en.teamshadowi

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

class SortFilter :
    UriPartFilter(
        "Sort By",
        arrayOf(
            Pair("Rating", "rating"),
            Pair("Latest", "created"),
            Pair("Views", "views"),
            Pair("Title", "title"),
        ),
    )

class GenreFilter :
    UriPartFilter(
        "Genre",
        arrayOf(
            Pair("All", "all"),
            Pair("Action", "action"),
            Pair("Adventure", "adventure"),
            Pair("Comedy", "comedy"),
            Pair("Drama", "drama"),
            Pair("Ecchi", "ecchi"),
            Pair("Fantasy", "fantasy"),
            Pair("Isekai", "isekai"),
            Pair("Romance", "romance"),
        ),
    )
