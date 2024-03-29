package eu.kanade.tachiyomi.extension.en.vyvymanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jsoup.nodes.Document

abstract class SelectFilter(displayName: String, private val options: Array<Pair<String, String>>) :
    Filter.Select<String>(
        displayName,
        options.map { it.first }.toTypedArray(),
    ) {
    open val selected get() = options[state].second.takeUnless { it.isEmpty() }
}

class SearchType : SelectFilter(
    "Title should contain/begin/end with typed text",
    arrayOf(
        Pair("Contain", "0"),
        Pair("Begin", "1"),
        Pair("End", "2"),
    ),
)

class SearchDescription : Filter.CheckBox("Search In Description")

class AuthorSearchType : SelectFilter(
    "Author should contain/begin/end with typed text",
    arrayOf(
        Pair("Contain", "0"),
        Pair("Begin", "1"),
        Pair("End", "2"),
    ),
)

class AuthorFilter : Filter.Text("Author")

class StatusFilter : SelectFilter(
    "Status",
    arrayOf(
        Pair("All", "2"),
        Pair("Ongoing", "0"),
        Pair("Completed", "1"),
    ),
)

class SortFilter : SelectFilter(
    "Sort by",
    arrayOf(
        Pair("Viewed", "viewed"),
        Pair("Scored", "scored"),
        Pair("Newest", "created_at"),
        Pair("Latest Update", "updated_at"),
    ),
)

class SortType : SelectFilter(
    "Sort order",
    arrayOf(
        Pair("Descending", "desc"),
        Pair("Ascending", "asc"),
    ),
)

class Genre(name: String, val id: String) : Filter.TriState(name)

class GenreFilter : Filter.Group<Genre>("Genre", genrePairs.map { Genre(it.name, it.id) })

private var genrePairs: List<Genre> = emptyList()

private val scope = CoroutineScope(Dispatchers.IO)

fun launchIO(block: () -> Unit) = scope.launch { block() }

private var fetchGenresAttempts: Int = 0

fun fetchGenres(baseUrl: String, headers: okhttp3.Headers, client: okhttp3.OkHttpClient) {
    if (fetchGenresAttempts < 3 && genrePairs.isEmpty()) {
        try {
            genrePairs =
                client.newCall(genresRequest(baseUrl, headers)).execute()
                    .asJsoup()
                    .let(::parseGenres)
        } catch (_: Exception) {
        } finally {
            fetchGenresAttempts++
        }
    }
}

private fun genresRequest(baseUrl: String, headers: okhttp3.Headers) = GET("$baseUrl/search", headers)

private const val genresSelector = ".check-genre div div:has(.checkbox-genre)"

private fun parseGenres(document: Document): List<Genre> {
    val items = document.select(genresSelector)
    return buildList(items.size) {
        items.mapTo(this) {
            Genre(
                it.select("label").text(),
                it.select(".checkbox-genre").attr("data-value"),
            )
        }
    }
}
