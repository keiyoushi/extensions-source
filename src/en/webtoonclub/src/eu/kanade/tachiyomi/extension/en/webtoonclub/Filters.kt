package eu.kanade.tachiyomi.extension.en.webtoonclub

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

// Default is relevance for a text search and Latest otherwise
class SortFilter(default: String = "") :
    SelectFilter(
        "Sort",
        "sort",
        listOf(
            "Default" to "",
            "Latest" to "latest",
            "Popular" to "popular",
            "Top rated" to "rating",
            "A-Z" to "a-z",
        ),
        default,
    )

class StatusFilter :
    SelectFilter(
        "Status",
        "status",
        listOf(
            "Any" to "",
            "Ongoing" to "ongoing",
            "Completed" to "completed",
            "Hiatus" to "hiatus",
            "Cancelled" to "cancelled",
        ),
    )

val RATING_OPTIONS = listOf(
    "Default (all)" to "",
    "Up to Safe" to "safe",
    "Up to Suggestive" to "suggestive",
    "Up to Erotica" to "erotica",
)

class RatingFilter(default: String) : SelectFilter("Content rating", "rating", RATING_OPTIONS, default)

class CheckBoxOption(name: String, val value: String) : Filter.CheckBox(name)

class TypeFilter :
    Filter.Group<CheckBoxOption>(
        "Type",
        listOf(
            "Manhwa" to "manhwa",
            "Manga" to "manga",
            "Manhua" to "manhua",
            "Comic" to "comic",
        ).map { CheckBoxOption(it.first, it.second) },
    ),
    UrlFilter {
    override fun addToUrl(url: HttpUrl.Builder) {
        val types = state.filter { it.state }.joinToString(",") { it.value }
        if (types.isNotEmpty()) url.addQueryParameter("types", types)
    }
}

class TriStateOption(name: String, val value: String) : Filter.TriState(name)

class GenreFilter(genres: List<Pair<String, String>>) :
    Filter.Group<TriStateOption>("Genres", genres.map { TriStateOption(it.first, it.second) }),
    UrlFilter {
    override fun addToUrl(url: HttpUrl.Builder) {
        val included = state.filter { it.state == Filter.TriState.STATE_INCLUDE }.joinToString(",") { it.value }
        val excluded = state.filter { it.state == Filter.TriState.STATE_EXCLUDE }.joinToString(",") { it.value }
        if (included.isNotEmpty()) url.addQueryParameter("genres", included)
        if (excluded.isNotEmpty()) url.addQueryParameter("excludeGenres", excluded)
    }
}
