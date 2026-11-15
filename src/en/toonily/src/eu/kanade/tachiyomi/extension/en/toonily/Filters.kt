package eu.kanade.tachiyomi.extension.en.toonily

import eu.kanade.tachiyomi.multisrc.madara.GenreRoute
import eu.kanade.tachiyomi.source.model.Filter
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl

@Serializable
class FilterData(
    val genres: List<GenreRoute>,
    val tags: List<String>,
)

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

open class SelectFilter(name: String, private val param: String, private val options: List<Pair<String, String>>) :
    Filter.Select<String>(name, options.map { it.first }.toTypedArray()),
    UrlFilter {
    val value get() = options[state].second

    override fun addTo(url: HttpUrl.Builder) {
        if (value.isNotEmpty()) url.addQueryParameter(param, value)
    }
}

class SortFilter(name: String, options: List<Pair<String, String>>) : SelectFilter(name, "m_orderby", options)

class AdultFilter(name: String, options: List<Pair<String, String>>) : SelectFilter(name, "adult", options)

class GenreConditionFilter(name: String, options: List<Pair<String, String>>) : SelectFilter(name, "op", options)

class CheckBoxOption(name: String, val value: String) : Filter.CheckBox(name)

open class CheckBoxGroup(name: String, private val param: String, options: List<Pair<String, String>>) :
    Filter.Group<CheckBoxOption>(name, options.map { CheckBoxOption(it.first, it.second) }),
    UrlFilter {
    override fun addTo(url: HttpUrl.Builder) {
        state.filter { it.state }.forEach { url.addQueryParameter(param, it.value) }
    }
}

class StatusFilter(name: String, options: List<Pair<String, String>>) : CheckBoxGroup(name, "status[]", options)

class GenreFilter(name: String, genres: List<GenreRoute>) : CheckBoxGroup(name, "genre[]", genres.map { it.name to it.slug })

class TagFilter(tags: List<String>) : Filter.Select<String>("Tag", arrayOf("None") + tags.map(::tagName).toTypedArray()) {
    private val slugs = listOf("") + tags

    val slug get() = slugs[state]
}

private fun tagName(slug: String) = slug.split('-').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
