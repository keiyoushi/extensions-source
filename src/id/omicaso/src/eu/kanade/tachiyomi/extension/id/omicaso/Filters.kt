package eu.kanade.tachiyomi.extension.id.omicaso

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(displayName: String, private val vals: List<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

class SortFilter :
    UriPartFilter(
        "Sort",
        listOf(
            "Latest update" to "updated",
            "Newest" to "created",
        ),
    )

class ModeFilter :
    UriPartFilter(
        "Audience",
        listOf(
            "All" to "all",
            "General" to "general",
            "Adult" to "adult",
        ),
    )

class GenreFilter(genres: List<GenreDto>) :
    UriPartFilter(
        "Genre",
        listOf("All" to "") + genres.map { it.name to it.value },
    )
