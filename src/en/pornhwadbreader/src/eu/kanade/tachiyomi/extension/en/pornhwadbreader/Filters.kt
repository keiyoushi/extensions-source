package eu.kanade.tachiyomi.extension.en.pornhwadbreader

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UrlFilter {
    fun addToUrl(url: HttpUrl.Builder)
}

open class SelectFilter(
    name: String,
    private val param: String,
    private val options: List<Pair<String, String>>,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()),
    UrlFilter {
    override fun addToUrl(url: HttpUrl.Builder) {
        val value = options[state].second
        if (value.isNotEmpty()) url.addQueryParameter(param, value)
    }
}

// Default is relevance for a text search and Latest otherwise
class SortFilter :
    SelectFilter(
        "Sort by",
        "sort",
        listOf(
            "Default" to "",
            "Latest" to "latest",
            "Newest series" to "added",
            "Top rated" to "rating",
            "Trending" to "trending",
            "Most read" to "read",
            "Most rated" to "rated",
            "A-Z" to "title",
            "Most chapters" to "chapters",
        ),
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
        ),
    )

class RatingFilter :
    SelectFilter(
        "Rating",
        "rating",
        listOf(
            "Any" to "",
            "3+" to "3",
            "3.5+" to "3.5",
            "4+" to "4",
            "4.5+" to "4.5",
        ),
    )

class LengthFilter :
    SelectFilter(
        "Length",
        "len",
        listOf(
            "Any" to "",
            "Under 20 chapters" to "short",
            "20 to 60 chapters" to "medium",
            "60+ chapters" to "long",
        ),
    )

class UpdatedFilter :
    SelectFilter(
        "Updated",
        "updated",
        listOf(
            "Any time" to "",
            "This week" to "7",
            "This month" to "30",
        ),
    )

class OrientationFilter :
    SelectFilter(
        "Orientation",
        "orientation",
        listOf(
            "Any" to "",
            "Yaoi" to "yaoi",
            "Yuri" to "yuri",
        ),
    )

class YearFilter(years: List<String>) : SelectFilter("Released", "year", listOf("Any" to "") + years.map { it to it })

class UncensoredFilter :
    Filter.CheckBox("Has uncensored edition"),
    UrlFilter {
    override fun addToUrl(url: HttpUrl.Builder) {
        if (state) url.addQueryParameter("unc", "1")
    }
}

class GenreTriState(name: String) : Filter.TriState(name)

class GenreFilter(genres: List<String>) :
    Filter.Group<GenreTriState>("Genres", genres.map(::GenreTriState)),
    UrlFilter {
    override fun addToUrl(url: HttpUrl.Builder) {
        state.forEach {
            when {
                it.isIncluded() -> url.addQueryParameter("g", it.name)
                it.isExcluded() -> url.addQueryParameter("xg", it.name)
            }
        }
    }
}
