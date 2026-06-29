package eu.kanade.tachiyomi.extension.en.arvenscans

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
    defaultValue: String? = null,
) : Filter.Select<String>(
    name,
    options.map { it.first }.toTypedArray(),
    options.indexOfFirst { it.second == defaultValue }.takeIf { it != -1 } ?: 0,
),
    UrlPartFilter {
    override fun addUrlParameter(url: HttpUrl.Builder) {
        options[state].second.takeIf { it.isNotBlank() }?.let {
            url.addQueryParameter(urlParameter, it)
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
    defaultValue: String? = null,
) : SelectFilter(title, key, options, defaultValue)

class SortDirectionFilter(
    title: String,
    key: String,
    options: Options,
    defaultValue: String? = null,
) : SelectFilter(title, key, options, defaultValue)

class GenreFilter(
    name: String,
    genres: Options,
) : Filter.Group<GenreValue>(
    name,
    genres.map { GenreValue(it.first, it.second) },
),
    UrlPartFilter {
    override fun addUrlParameter(url: HttpUrl.Builder) {
        val included = state
            .filter { it.state == Filter.TriState.STATE_INCLUDE }
            .map { it.id }

        val excluded = state
            .filter { it.state == Filter.TriState.STATE_EXCLUDE }
            .map { it.id }

        if (included.isNotEmpty()) {
            url.addQueryParameter(GENRE_INCLUDE_FILTER_KEY, included.joinToString(","))
        }

        if (excluded.isNotEmpty()) {
            url.addQueryParameter(GENRE_EXCLUDE_FILTER_KEY, excluded.joinToString(","))
        }
    }
}

class GenreValue(name: String, val id: String) : Filter.TriState(name)
