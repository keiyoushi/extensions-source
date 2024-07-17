package eu.kanade.tachiyomi.multisrc.iken

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UrlPartFilter {
    fun addUrlParameter(url: HttpUrl.Builder)
}

abstract class SelectFilter(
    name: String,
    private val urlParameter: String,
    private val options: List<Pair<String, String>>,
) : UrlPartFilter, Filter.Select<String>(
    name,
    options.map { it.first }.toTypedArray(),
) {
    override fun addUrlParameter(url: HttpUrl.Builder) {
        url.addQueryParameter(urlParameter, options[state].second)
    }
}

class CheckBoxFilter(name: String, val value: String) : Filter.CheckBox(name)

abstract class CheckBoxGroup(
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
            url.addQueryParameter(urlParameter, checked.joinToString(","))
        }
    }
}

class StatusFilter : SelectFilter(
    "Status",
    "seriesStatus",
    listOf(
        Pair("", ""),
        Pair("Ongoing", "ONGOING"),
        Pair("Completed", "COMPLETED"),
        Pair("Cancelled", "CANCELLED"),
        Pair("Dropped", "DROPPED"),
        Pair("Mass Released", "MASS_RELEASED"),
        Pair("Coming Soon", "COMING_SOON"),
    ),
)

class TypeFilter : SelectFilter(
    "Type",
    "seriesType",
    listOf(
        Pair("", ""),
        Pair("Manga", "MANGA"),
        Pair("Manhua", "MANHUA"),
        Pair("Manhwa", "MANHWA"),
        Pair("Russian", "RUSSIAN"),
    ),
)

class GenreFilter(genres: List<Pair<String, String>>) : CheckBoxGroup(
    "Genres",
    "genreIds",
    genres,
)
