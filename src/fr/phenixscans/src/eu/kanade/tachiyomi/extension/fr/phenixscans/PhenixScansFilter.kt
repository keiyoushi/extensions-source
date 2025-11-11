package eu.kanade.tachiyomi.extension.fr.phenixscans

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import keiyoushi.utils.parseAs
import okhttp3.Headers
import okhttp3.OkHttpClient
import kotlin.concurrent.thread

// ========================= Sorting & Filtering ==========================

class SortFilter : UriPartFilter(
    "Sort by",
    arrayOf(
        Pair("Alphabetic", "title"),
        Pair("Rating", "rating"),
        Pair("Last updated", "updatedAt"),
        Pair("Chapter number", "chapters"),
    ),
)

class Tag(name: String, val id: String) : Filter.CheckBox(name)

class GenreFilter(genres: List<Tag>) : Filter.Group<Tag>(
    "Genres",
    genres,
)

class StatusFilter : UriPartFilter(
    "Status",
    arrayOf(
        Pair("All status", ""),
        Pair("Ongoing", "Ongoing"),
        Pair("On Hiatus", "Hiatus"),
        Pair("Completed", "Completed"),
    ),
)

class TypeFilter : UriPartFilter(
    "Type",
    arrayOf(
        Pair("Any type", ""),
        Pair("Manga", "Manga"),
        Pair("Manhwa", "Manhwa"),
        Pair("Manhua", "Manhua"),
    ),
)

fun getGlobalFilterList(apiBaseUrl: String, client: OkHttpClient, headers: Headers): FilterList {
    fetchFilters(apiBaseUrl, client, headers)
    val filters = mutableListOf<Filter<*>>(
        Filter.Header("Filters are not compatible with text-based search"),
        Filter.Separator(),

        Filter.Header("Type"),
        TypeFilter(),
        Filter.Separator(),

        Filter.Header("Sort by"),
        SortFilter(),
        Filter.Separator(),

        Filter.Header("Status"),
        StatusFilter(),
        Filter.Separator(),
    )

    if (filtersState == FiltersState.FETCHED) {
        filters += listOf(
            Filter.Separator(),
            Filter.Header("Filter by genres"),
            GenreFilter(genresList),
        )
    } else {
        filters += listOf(
            Filter.Separator(),
            Filter.Header("Click on 'Reset' to load missing filters"),
        )
    }

    return FilterList(filters)
}

private var genresList: List<Tag> = emptyList()
private var fetchFiltersAttempts = 0
private var filtersState = FiltersState.NOT_FETCHED

private fun fetchFilters(apiBaseUrl: String, client: OkHttpClient, headers: Headers) {
    if (filtersState != FiltersState.NOT_FETCHED || fetchFiltersAttempts >= 3) return
    filtersState = FiltersState.FETCHING
    fetchFiltersAttempts++
    thread {
        try {
            val response = client.newCall(GET("$apiBaseUrl/genres", headers)).execute()
            val filters = response.parseAs<GenreListDto>()

            genresList = filters.data.map { Tag(it.name, it.id) }

            filtersState = FiltersState.FETCHED
        } catch (e: Throwable) {
            filtersState = FiltersState.NOT_FETCHED
        }
    }
}

open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
    Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

private enum class FiltersState { NOT_FETCHED, FETCHING, FETCHED }
