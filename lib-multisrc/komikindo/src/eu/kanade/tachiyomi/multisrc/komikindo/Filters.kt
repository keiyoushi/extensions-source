package eu.kanade.tachiyomi.multisrc.komikindo

import eu.kanade.tachiyomi.source.model.Filter
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl

interface UrlPartFilter {
    fun addUrlParameter(url: HttpUrl.Builder)
}

class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
    private val urlParameter: String,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()),
    UrlPartFilter {
    override fun addUrlParameter(url: HttpUrl.Builder) {
        url.addQueryParameter(urlParameter, options[state].second)
    }
}

class CheckBoxFilter(name: String, val value: String) : Filter.CheckBox(name)

class CheckBoxGroup(
    name: String,
    options: List<Pair<String, String>>,
    private val urlParameter: String,
) : Filter.Group<CheckBoxFilter>(name, options.map { CheckBoxFilter(it.first, it.second) }),
    UrlPartFilter {
    override fun addUrlParameter(url: HttpUrl.Builder) {
        state.filter { it.state }.forEach { url.addQueryParameter(urlParameter, it.value) }
    }
}

@Serializable
class FilterGroup(
    val name: String,
    val param: String,
    val options: List<Pair<String, String>>,
)
