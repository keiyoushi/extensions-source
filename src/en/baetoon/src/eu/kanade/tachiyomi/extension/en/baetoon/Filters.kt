package eu.kanade.tachiyomi.extension.en.baetoon

import eu.kanade.tachiyomi.source.model.Filter

open class SelectFilter(name: String, private val options: List<Pair<String, String>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val value get() = options[state].second
}

class SortFilter :
    SelectFilter(
        "Sort by",
        listOf(
            "Most Viewed" to "most-view",
            "Recently Added" to "recent-add",
            "Latest Updates" to "lastest-updates",
            "Top Rated" to "top-rates",
            "Alphabetical (A-Z)" to "a-z",
        ),
    )

class AuthorFilter : Filter.Text("Author")

class KeywordFilter : Filter.Text("Keyword")

class GenreFilter(genres: List<String>) : SelectFilter("Genre", listOf("All Genres" to "") + genres.map { it to it })
