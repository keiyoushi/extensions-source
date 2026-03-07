package eu.kanade.tachiyomi.multisrc.iken

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

typealias Options = List<Pair<String, String>>

interface UrlPartFilter {
    fun addUrlParameter(url: HttpUrl.Builder)
}

abstract class SelectFilter(
    name: String,
    private val urlParameter: String,
    private val options: Options,
) : Filter.Select<String>(
    name,
    options.map { it.first }.toTypedArray(),
),
    UrlPartFilter {
    override fun addUrlParameter(url: HttpUrl.Builder) {
        url.addQueryParameter(urlParameter, options[state].second)
    }
}

class CheckBoxFilter(
    name: String,
    val value: String,
) : Filter.CheckBox(name)

abstract class CheckBoxGroup(
    name: String,
    private val urlParameter: String,
    options: Options,
) : Filter.Group<CheckBoxFilter>(
    name,
    options.map { CheckBoxFilter(it.first, it.second) },
),
    UrlPartFilter {
    override fun addUrlParameter(url: HttpUrl.Builder) {
        val checked = state.filter { it.state }.map { it.value }

        if (checked.isNotEmpty()) {
            url.addQueryParameter(urlParameter, checked.joinToString(","))
        }
    }
}

class StatusFilter(
    title: String,
    key: String,
    options: Options,
) : SelectFilter(title, key, options)

class TypeFilter(
    title: String,
    key: String,
    options: Options,
) : SelectFilter(title, key, options)

class SortFilter(
    title: String,
    key: String,
    options: Options,
) : SelectFilter(title, key, options)

class GenreFilter(
    title: String,
    key: String,
    genres: Options,
) : CheckBoxGroup(title, key, genres)
