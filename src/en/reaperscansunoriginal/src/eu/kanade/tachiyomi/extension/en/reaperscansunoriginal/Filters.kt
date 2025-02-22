package eu.kanade.tachiyomi.extension.en.reaperscansunoriginal

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.HttpUrl

interface UrlPartFilter {
    fun addUrlParameter(url: HttpUrl.Builder)
}

abstract class SelectFilter(
    name: String,
    private val urlParameter: String,
    private val options: List<Pair<String, String>>,
    defaultValue: String? = null,
) : UrlPartFilter, Filter.Select<String>(
    name,
    options.map { it.first }.toTypedArray(),
    options.indexOfFirst { it.second == defaultValue }.coerceAtLeast(0),
) {
    override fun addUrlParameter(url: HttpUrl.Builder) {
        url.addQueryParameter(urlParameter, options[state].second)
    }
}

class CheckBoxFilter(name: String, val value: String) : Filter.CheckBox(name)

open class CheckBoxGroup(
    name: String,
    private val urlParameter: String,
    options: List<Pair<String, String>>,
) : UrlPartFilter, Filter.Group<CheckBoxFilter>(
    name,
    options.map { CheckBoxFilter(it.first, it.second) },
) {
    override fun addUrlParameter(url: HttpUrl.Builder) {
        val checked = state.filter { it.state }.map { it.value }

        if (checked.isNotEmpty()) {
            checked.forEach { genre ->
                url.addQueryParameter(urlParameter, genre)
            }
        }
    }
}

class TypeFilter : CheckBoxGroup(
    "Status",
    "type[]",
    listOf(
        Pair("Action", "action"),
        Pair("Adventure", "adventure"),
        Pair("Fantasy", "fantasy"),
        Pair("Manga", "manga"),
        Pair("Manhua", "manhua"),
        Pair("Manhwa", "manhwa"),
        Pair("Seinen", "seinen"),
    ),
)

class GenreFilter(genres: List<Pair<String, String>>) : CheckBoxGroup(
    "Genres",
    "genre[]",
    genres,
)

class YearFilter : CheckBoxGroup(
    "Status",
    "release[]",
    listOf(
        Pair("2024", "2024"),
        Pair("2023", "2023"),
        Pair("2022", "2022"),
        Pair("2021", "2021"),
        Pair("2020", "2020"),
        Pair("2019", "2019"),
        Pair("2018", "2018"),
        Pair("2017", "2017"),
        Pair("2016", "2016"),
        Pair("2015", "2015"),
    ),
)

class StatusFilter : CheckBoxGroup(
    "Status",
    "status[]",
    listOf(
        Pair("Releasing", "on-going"),
        Pair("Completed", "end"),
    ),
)

class OrderFilter(default: String? = null) : SelectFilter(
    "Sort by",
    "sort",
    listOf(
        Pair("", ""),
        Pair("Popular", "most_viewed"),
        Pair("Latest", "recently_added"),
    ),
    default,
) {
    companion object {
        val POPULAR = FilterList(OrderFilter("most_viewed"))
        val LATEST = FilterList(OrderFilter("recently_added"))
    }
}
