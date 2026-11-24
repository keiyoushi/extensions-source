package eu.kanade.tachiyomi.extension.en.manhuatop

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UrlFilter {
    fun addToUrl(url: HttpUrl.Builder)
}

open class SelectFilter(
    name: String,
    private val param: String,
    private val options: List<Pair<String, String>>,
    defaultValue: String = "",
) : Filter.Select<String>(
    name,
    options.map { it.first }.toTypedArray(),
    options.indexOfFirst { it.second == defaultValue }.coerceAtLeast(0),
),
    UrlFilter {
    override fun addToUrl(url: HttpUrl.Builder) {
        val value = options[state].second
        if (value.isNotEmpty()) url.addQueryParameter(param, value)
    }
}

// Default is relevance for a text search and Latest Update otherwise
class SortFilter(default: String = "") :
    SelectFilter(
        "Sort by",
        "sort",
        listOf(
            "Default" to "",
            "Latest Update" to "latest_update",
            "Newest" to "newest",
            "Title" to "title",
            "Highest Rated" to "rating",
            "Most Viewed" to "views",
            "Trending This Week" to "trending-week",
            "Trending This Month" to "trending-month",
            "Trending This Year" to "trending-year",
        ),
        default,
    )

class TypeFilter :
    SelectFilter(
        "Type",
        "manga_type",
        listOf(
            "All types" to "",
            "Manhwa" to "Manhwa",
            "Manga" to "Manga",
            "Manhua" to "Manhua",
            "Webtoons" to "Webtoons",
        ),
    )

class StatusFilter :
    SelectFilter(
        "Status",
        "status",
        listOf(
            "All statuses" to "",
            "Ongoing" to "on-going",
            "Completed" to "end",
            "On hold" to "on-hold",
            "Canceled" to "canceled",
        ),
    )

class GenreOption(name: String, val id: String) : Filter.CheckBox(name)

class GenreFilter(genres: List<Pair<String, String>>) :
    Filter.Group<GenreOption>("Genres (any of, up to 10)", genres.map { GenreOption(it.first, it.second) }),
    UrlFilter {
    override fun addToUrl(url: HttpUrl.Builder) {
        val ids = state.filter { it.state }.map { it.id }
        require(ids.size <= 10) { "Select up to 10 genres" }
        if (ids.isNotEmpty()) url.addQueryParameter("genre", ids.joinToString(","))
    }
}
