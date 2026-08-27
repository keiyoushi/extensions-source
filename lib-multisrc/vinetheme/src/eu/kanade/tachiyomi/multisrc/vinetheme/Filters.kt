package eu.kanade.tachiyomi.multisrc.vinetheme

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UriQueryFilter {
    fun addToQuery(builder: HttpUrl.Builder)
}

open class UriPartFilter(
    name: String,
    private val field: String,
    private val vals: Array<Pair<String, String>>,
    private val default: String = "",
) : Filter.Select<String>(
    name,
    vals.map { it.first }.toTypedArray(),
    vals.indexOfFirst { it.second == default }.takeIf { it != -1 } ?: 0,
),
    UriQueryFilter {
    override fun addToQuery(builder: HttpUrl.Builder) {
        val selected = vals[state].second
        if (selected.isNotEmpty()) {
            builder.addQueryParameter(field, selected)
        }
    }
}

class SortFilter :
    UriPartFilter(
        "Sort",
        "sort",
        arrayOf(
            Pair("Latest", "updated"),
            Pair("Popular", "popular"),
            Pair("Trending", "trending"),
            Pair("Views", "views"),
            Pair("Rating", "rating"),
            Pair("Longest", "longest"),
            Pair("Newest", "newest"),
        ),
        default = "updated",
    )

class StatusFilter :
    UriPartFilter(
        "Status",
        "status",
        arrayOf(
            Pair("All", ""),
            Pair("Ongoing", "Ongoing"),
            Pair("Completed", "Completed"),
            Pair("Hiatus", "Hiatus"),
            Pair("Dropped", "Dropped"),
            Pair("Discontinued", "Discontinued"),
            Pair("Upcoming", "Upcoming"),
        ),
    )

class TypeFilter :
    UriPartFilter(
        "Type",
        "type",
        arrayOf(
            Pair("All", ""),
            Pair("Manhwa", "MANHWA"),
            Pair("Manhua", "MANHUA"),
            Pair("Manga", "MANGA"),
        ),
    )

class OriginFilter :
    UriPartFilter(
        "Origin",
        "origin",
        arrayOf(
            Pair("All", ""),
            Pair("Korean", "KOREAN"),
            Pair("Japanese", "JAPANESE"),
            Pair("Chinese", "CHINESE"),
            Pair("Other", "OTHER"),
        ),
    )

class GenreFilter(genres: Array<Pair<String, String>>) :
    Filter.Group<GenreFilter.GenreCheckBox>(
        "Genre",
        genres.map { GenreCheckBox(it.first, it.second) },
    ),
    UriQueryFilter {
    class GenreCheckBox(name: String, val value: String) : Filter.CheckBox(name)

    override fun addToQuery(builder: HttpUrl.Builder) {
        val selected = state.filter { it.state }.map { it.value }
        if (selected.isNotEmpty()) {
            builder.addQueryParameter("genre", selected.joinToString(","))
        }
    }
}
