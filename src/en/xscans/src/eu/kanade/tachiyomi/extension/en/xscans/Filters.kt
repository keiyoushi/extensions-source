package eu.kanade.tachiyomi.extension.en.xscans

import eu.kanade.tachiyomi.source.model.Filter

abstract class SelectFilter(
    name: String,
    private val options: Array<Pair<String, String>>,
) : Filter.Select<String>(name, options.map { it.second }.toTypedArray()) {
    val selected get() = options[state].first
}

class SortFilter :
    SelectFilter(
        "Sort By",
        arrayOf(
            Pair("popular", "Popular"),
            Pair("newest", "Newest"),
            Pair("updated", "Updated"),
        ),
    )

class GenreFilter :
    SelectFilter(
        "Genre",
        arrayOf(
            Pair("", "All"),
            Pair("Action", "Action"),
            Pair("Adventure", "Adventure"),
            Pair("Comedy", "Comedy"),
            Pair("Crime", "Crime"),
            Pair("Drama", "Drama"),
            Pair("Fantasy", "Fantasy"),
            Pair("Historical", "Historical"),
            Pair("Horror", "Horror"),
            Pair("Isekai", "Isekai"),
            Pair("Mystery", "Mystery"),
            Pair("Psychological", "Psychological"),
            Pair("Romance", "Romance"),
            Pair("Slice of Life", "Slice of Life"),
            Pair("Sports", "Sports"),
            Pair("Tragedy", "Tragedy"),
        ),
    )

class DemographicFilter :
    SelectFilter(
        "Demographic",
        arrayOf(
            Pair("", "All"),
            Pair("Shounen(B)", "Shounen"),
            Pair("Seinen(M)", "Seinen"),
            Pair("Shoujo(G)", "Shoujo"),
            Pair("Josei(W)", "Josei"),
        ),
    )
