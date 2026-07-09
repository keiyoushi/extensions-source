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

class GenreFilterGroup :
    Filter.Group<GenreFilter>(
        "Genres",
        listOf(
            GenreFilter("Yaoi"),
            GenreFilter("Doujinshi"),
            GenreFilter("Shounen Ai"),
            GenreFilter("Shoujo"),
            GenreFilter("Yuri"),
            GenreFilter("Romance"),
            GenreFilter("Fantasy"),
            GenreFilter("Comedy"),
            GenreFilter("Smut"),
            GenreFilter("Adult"),
            GenreFilter("School Life"),
            GenreFilter("Mystery"),
            GenreFilter("One Shot"),
            GenreFilter("Ecchi"),
            GenreFilter("Shounen"),
            GenreFilter("Martial Arts"),
            GenreFilter("Shoujo Ai"),
            GenreFilter("Supernatural"),
            GenreFilter("Drama"),
            GenreFilter("Action"),
            GenreFilter("Adventure"),
            GenreFilter("Harem"),
            GenreFilter("Historical"),
            GenreFilter("Horror"),
            GenreFilter("Josei"),
            GenreFilter("Mature"),
            GenreFilter("Mecha"),
            GenreFilter("Psychological"),
            GenreFilter("Sci-fi"),
            GenreFilter("Seinen"),
            GenreFilter("Slice Of Life"),
            GenreFilter("Sports"),
            GenreFilter("Gender Bender"),
            GenreFilter("Tragedy"),
            GenreFilter("Bara"),
            GenreFilter("Shotacon"),
            GenreFilter("Webtoons"),
        ),
    )
