package eu.kanade.tachiyomi.extension.vi.khomanhwa

import eu.kanade.tachiyomi.source.model.Filter
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl

@Serializable
class FilterOption(
    val name: String,
    val value: String,
)

@Serializable
class FilterData(
    val genres: List<FilterOption>,
)

class CategoryFilter :
    Filter.Select<String>(
        "Category",
        arrayOf("All", "VIP", "Uncensored", "Completed"),
    ) {
    fun isCategorySelected() = state > 0

    fun getCategoryPath(): String? = when (state) {
        1 -> "vip"
        2 -> "uncensored"
        3 -> "completed"
        else -> null
    }
}

class GenreFilter(options: List<FilterOption>) :
    Filter.Select<String>(
        "Genre",
        options.map { it.name }.toTypedArray(),
    ) {
    private val entries = options

    fun addQueryParameter(url: HttpUrl.Builder) {
        val value = entries[state].value
        if (value.isNotEmpty()) {
            url.addQueryParameter("genre", value)
        }
    }
}

class StatusFilter(options: List<FilterOption>) :
    Filter.Select<String>(
        "Status",
        options.map { it.name }.toTypedArray(),
    ) {
    private val entries = options

    fun addQueryParameter(url: HttpUrl.Builder) {
        val value = entries[state].value
        if (value.isNotEmpty()) {
            url.addQueryParameter("status", value)
        }
    }
}

class SortFilter(options: List<FilterOption>) :
    Filter.Select<String>(
        "Sort",
        options.map { it.name }.toTypedArray(),
    ) {
    private val entries = options

    fun addQueryParameter(url: HttpUrl.Builder) {
        val value = entries[state].value
        if (value.isNotEmpty()) {
            url.addQueryParameter("sort", value)
        }
    }
}

val STATUS_OPTIONS = listOf(
    FilterOption("All", ""),
    FilterOption("Ongoing", "Ongoing"),
    FilterOption("Completed", "Completed"),
    FilterOption("Hiatus", "Hiatus"),
)

val SORT_OPTIONS = listOf(
    FilterOption("Updated", "updated"),
    FilterOption("Popular", "popular"),
    FilterOption("Most Chapters", "chapters"),
    FilterOption("Newest", "newest"),
    FilterOption("A-Z", "az"),
    FilterOption("Z-A", "za"),
)
