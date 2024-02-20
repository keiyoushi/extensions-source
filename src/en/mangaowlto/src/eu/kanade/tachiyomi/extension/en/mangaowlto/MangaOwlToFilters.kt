package eu.kanade.tachiyomi.extension.en.mangaowlto

import eu.kanade.tachiyomi.source.model.Filter

class Genre(name: String, val uriPart: String) : Filter.CheckBox(name)

class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)

class SortFilter : UriPartFilter(
    "Sort by",
    arrayOf(
        Pair("Default", null),
        Pair("Most view", "-view_count"),
        Pair("Added", "created_at"),
        Pair("Last update", "-modified_at"),
        Pair("High rating", "rating"),
    ),
)

class StatusFilter : UriPartFilter(
    "Status",
    arrayOf(
        Pair("Any", null),
        Pair("Completed", MangaOwlTo.COMPLETED),
        Pair("Ongoing", MangaOwlTo.ONGOING),
    ),
)

open class UriPartFilter(displayName: String, private val pairs: Array<Pair<String, String?>>) :
    Filter.Select<String>(displayName, pairs.map { it.first }.toTypedArray()) {
    fun toUriPart() = pairs[state].second
}
