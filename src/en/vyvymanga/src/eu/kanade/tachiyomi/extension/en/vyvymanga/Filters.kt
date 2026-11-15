package eu.kanade.tachiyomi.extension.en.vyvymanga

import eu.kanade.tachiyomi.source.model.Filter
import kotlinx.serialization.Serializable

abstract class SelectFilter(displayName: String, private val options: Array<Pair<String, String>>) :
    Filter.Select<String>(
        displayName,
        options.map { it.first }.toTypedArray(),
    ) {
    open val selected get() = options[state].second.takeUnless { it.isEmpty() }
}

class SearchType :
    SelectFilter(
        "Title should contain/begin/end with typed text",
        arrayOf(
            Pair("Contain", "0"),
            Pair("Begin", "1"),
            Pair("End", "2"),
        ),
    )

class SearchDescription : Filter.CheckBox("Search In Description")

class AuthorSearchType :
    SelectFilter(
        "Author should contain/begin/end with typed text",
        arrayOf(
            Pair("Contain", "0"),
            Pair("Begin", "1"),
            Pair("End", "2"),
        ),
    )

class AuthorFilter : Filter.Text("Author")

class StatusFilter :
    SelectFilter(
        "Status",
        arrayOf(
            Pair("All", "2"),
            Pair("Ongoing", "0"),
            Pair("Completed", "1"),
        ),
    )

class SortFilter :
    SelectFilter(
        "Sort by",
        arrayOf(
            Pair("Viewed", "viewed"),
            Pair("Scored", "scored"),
            Pair("Newest", "created_at"),
            Pair("Latest Update", "updated_at"),
        ),
    )

class SortType :
    SelectFilter(
        "Sort order",
        arrayOf(
            Pair("Descending", "desc"),
            Pair("Ascending", "asc"),
        ),
    )

@Serializable
class GenreData(
    val name: String,
    val id: String,
) {
    fun toGenre() = Genre(
        name = name,
        id = id,
    )
}

class Genre(name: String, val id: String) : Filter.TriState(name)

class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Genre", genres)
