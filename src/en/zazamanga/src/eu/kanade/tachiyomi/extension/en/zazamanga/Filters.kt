package eu.kanade.tachiyomi.extension.en.zazamanga

import eu.kanade.tachiyomi.source.model.Filter
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl

internal interface UriFilter {
    fun addToUri(builder: HttpUrl.Builder)
}

@Serializable
internal class GenreOption(val name: String, val slug: String)

internal class TextFilter(name: String, private val key: String) :
    Filter.Text(name),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        if (state.isNotBlank()) builder.addQueryParameter(key, state.trim())
    }
}

internal class SelectFilter(name: String, private val key: String, private val options: List<Pair<String, String>>) :
    Filter.Select<String>(name, options.map { it.first }.toTypedArray()),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        options[state].second.takeIf(String::isNotEmpty)?.let { builder.addQueryParameter(key, it) }
    }
}

internal class CheckBoxOption(name: String, val value: String) : Filter.CheckBox(name)

internal class CheckBoxGroup(name: String, private val key: String, options: List<Pair<String, String>>) :
    Filter.Group<CheckBoxOption>(name, options.map { CheckBoxOption(it.first, it.second) }),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        state.filter { it.state }.forEach { builder.addQueryParameter(key, it.value) }
    }
}
