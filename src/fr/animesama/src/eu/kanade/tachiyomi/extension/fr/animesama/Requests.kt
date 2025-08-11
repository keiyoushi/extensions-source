package eu.kanade.tachiyomi.extension.fr.animesama

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UrlPartFilter {
    fun addUrlParameter(url: HttpUrl.Builder)
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

class GenreFilter(
    options: List<Pair<String, String>>,
) : CheckBoxGroup(
    "Genres",
    options,
    "genre[0]",
)
