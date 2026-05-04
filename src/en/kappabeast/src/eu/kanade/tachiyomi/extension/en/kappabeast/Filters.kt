package eu.kanade.tachiyomi.extension.en.kappabeast

import eu.kanade.tachiyomi.source.model.Filter

open class SelectFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    val value: String
        get() = vals[state].second
}

class GenreFilter :
    SelectFilter(
        "Genre",
        arrayOf(
            Pair("All", ""),
            Pair("Fantasy", "Fantasy"),
            Pair("Romance", "Romance"),
            Pair("Comedy", "Comedy"),
            Pair("Drama", "Drama"),
            Pair("Thriller", "Thriller"),
            Pair("Action", "Action"),
            Pair("Psychological", "Psychological"),
            Pair("Isekai", "Isekai"),
        ),
    )

class StatusFilter :
    SelectFilter(
        "Status",
        arrayOf(
            Pair("All", ""),
            Pair("Ongoing", "Ongoing"),
            Pair("Completed", "Completed"),
            Pair("Hiatus", "Hiatus"),
        ),
    )

class TypeFilter :
    SelectFilter(
        "Type",
        arrayOf(
            Pair("All", ""),
            Pair("Manga", "Manga"),
            Pair("Manhwa", "Manhwa"),
            Pair("Manhua", "Manhua"),
        ),
    )

class SortFilter :
    SelectFilter(
        "Sort By",
        arrayOf(
            Pair("Popularity", ""),
            Pair("Latest Updates", "updatedAt:desc"),
            Pair("Newest", "createdAt:desc"),
            Pair("A-Z Name", "title:asc"),
        ),
    )
