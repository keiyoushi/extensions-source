package eu.kanade.tachiyomi.multisrc.fuzzydoodle

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UrlPartFilter {
    fun addUrlParameter(url: HttpUrl.Builder)
}

abstract class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
    private val urlParameter: String,
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
    options: List<Pair<String, String>>,
    private val urlParameter: String,
) : UrlPartFilter, Filter.Group<CheckBoxFilter>(
    name,
    options.map { CheckBoxFilter(it.first, it.second) },
) {
    override fun addUrlParameter(url: HttpUrl.Builder) {
        state.filter { it.state }.forEach {
            url.addQueryParameter(urlParameter, it.value)
        }
    }
}

class TypeFilter(
    options: List<Pair<String, String>>,
) : SelectFilter(
    "Type",
    options,
    "type",
)

class StatusFilter(
    options: List<Pair<String, String>>,
) : SelectFilter(
    "Status",
    options,
    "status",
)

class GenreFilter(
    options: List<Pair<String, String>>,
) : CheckBoxGroup(
    "Genres",
    options,
    "genre[]",
)
