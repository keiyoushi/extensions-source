package eu.kanade.tachiyomi.extension.en.mangago

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UriFilter {
    fun addToUrl(builder: HttpUrl.Builder)
}

class StatusFilter(name: String, val query: String, state: Boolean) :
    Filter.CheckBox(name, state),
    UriFilter {
    override fun addToUrl(builder: HttpUrl.Builder) {
        builder.addQueryParameter(query, if (state) "1" else "0")
    }
}

class StatusFilterGroup :
    Filter.Group<StatusFilter>(
        "Status",
        listOf(
            StatusFilter("Completed", "f", true),
            StatusFilter("Ongoing", "o", true),
        ),
    ),
    UriFilter {
    override fun addToUrl(builder: HttpUrl.Builder) {
        state.forEach { it.addToUrl(builder) }
    }
}

open class UriPartFilter(
    name: String,
    private val query: String,
    private val vals: Array<Pair<String, String>>,
    private val firstIsUnspecified: Boolean = true,
    state: Int = 0,
) : Filter.Select<String>(name, vals.map { it.first }.toTypedArray(), state),
    UriFilter {
    override fun addToUrl(builder: HttpUrl.Builder) {
        if (state != 0 || !firstIsUnspecified) {
            builder.addQueryParameter(query, vals[state].second)
        }
    }
}

class SortFilter :
    UriPartFilter(
        "Sort",
        "sortby",
        arrayOf(
            Pair("Random", "random"),
            Pair("Views", "view"),
            Pair("Comment Count", "comment_count"),
            Pair("Creation Date", "create_date"),
            Pair("Update Date", "update_date"),
        ),
        state = 1,
    )

class GenreFilter(name: String) : Filter.TriState(name)

class GenreFilterGroup(genres: List<String>) :
    Filter.Group<GenreFilter>(
        "Genres",
        genres.map(::GenreFilter),
    )
