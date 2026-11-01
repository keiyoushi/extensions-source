package eu.kanade.tachiyomi.extension.en.komix

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

open class SelectFilter(
    name: String,
    private val param: String,
    private val options: List<Pair<String, String>>,
    defaultValue: String = "",
) : Filter.Select<String>(
    name,
    options.map { it.first }.toTypedArray(),
    options.indexOfFirst { it.second == defaultValue }.coerceAtLeast(0),
) {
    fun addToUrl(url: HttpUrl.Builder) {
        val value = options[state].second
        if (value.isNotEmpty()) url.addQueryParameter(param, value)
    }
}

class SortFilter(default: String = "") :
    SelectFilter(
        "Sort",
        "sort",
        listOf(
            "Most Popular" to "",
            "Latest Update" to "date",
            "Recently Added" to "new",
            "Highest Rated" to "rating",
            "A → Z" to "az",
            "Z → A" to "za",
            "Best match" to "relevance",
        ),
        default,
    )

class GenreFilter(genres: List<GenreDto>) :
    SelectFilter(
        "Genre",
        "genre",
        listOf("Any" to "") + genres.map { it.name to it.slug },
    )

class StatusFilter :
    SelectFilter(
        "Status",
        "status",
        listOf(
            "Any" to "",
            "Releasing" to "ongoing",
            "Completed" to "completed",
            "On Hiatus" to "hiatus",
            "Cancelled" to "dropped",
        ),
    )

class TypeFilter :
    SelectFilter(
        "Type",
        "type",
        listOf(
            "Any" to "",
            "Manga" to "manga",
            "Manhwa" to "manhwa",
            "Manhua" to "manhua",
        ),
    )
