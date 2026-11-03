package eu.kanade.tachiyomi.extension.pt.hentaitecanet

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UrlFilter {
    fun addTo(url: HttpUrl.Builder)
}

class TextFilter(name: String, private val param: String) :
    Filter.Text(name),
    UrlFilter {
    override fun addTo(url: HttpUrl.Builder) {
        if (state.isNotBlank()) url.addQueryParameter(param, state.trim())
    }
}

class SelectFilter(name: String, private val param: String, private val options: List<Pair<String, String>>) :
    Filter.Select<String>(name, options.map { it.first }.toTypedArray()),
    UrlFilter {
    override fun addTo(url: HttpUrl.Builder) {
        options[state].second.takeIf(String::isNotEmpty)?.let { url.addQueryParameter(param, it) }
    }
}

class CheckBoxOption(name: String, val value: String) : Filter.CheckBox(name)

class CheckBoxGroup(name: String, private val param: String, options: List<Pair<String, String>>) :
    Filter.Group<CheckBoxOption>(name, options.map { CheckBoxOption(it.first, it.second) }),
    UrlFilter {
    override fun addTo(url: HttpUrl.Builder) {
        state.filter { it.state }.forEach { url.addQueryParameter(param, it.value) }
    }
}
