package eu.kanade.tachiyomi.extension.ja.rawdevartart

import eu.kanade.tachiyomi.source.model.Filter
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl

interface UriFilter {
    fun addToUri(builder: HttpUrl.Builder)
}

open class UriPartFilter(
    name: String,
    private val query: String,
    private val vals: Array<Pair<String, String>>,
    state: Int = 0,
) : Filter.Select<String>(name, vals.map { it.first }.toTypedArray(), state),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        builder.addQueryParameter(query, vals[state].second)
    }
}

class StatusFilter :
    UriPartFilter(
        "Status",
        "status",
        arrayOf(
            "All" to "",
            "Ongoing" to "ongoing",
            "Completed" to "completed",
        ),
    )

class SortFilter(state: Int = 1) :
    UriPartFilter(
        "Sort by",
        "sort",
        arrayOf(
            "Recently updated" to "",
            "Most viewed" to "most_viewed",
            "Most viewed today" to "most_viewed_today",
        ),
        state,
    )

@Serializable
data class Genre(val name: String, val path: String) {
    override fun toString() = name
}

val allGenre: Array<Genre> = arrayOf(Genre("All", "all"))

class GenreFilter(genres: Array<Genre> = emptyArray()) : Filter.Select<Genre>("Genre", allGenre + genres)
