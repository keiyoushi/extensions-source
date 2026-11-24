package eu.kanade.tachiyomi.extension.en.mangastash

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface QueryFilter {
    fun addToUrl(builder: HttpUrl.Builder)
}

class SelectFilter(
    name: String,
    private val param: String,
    private val options: List<FilterOption>,
    default: Int = 0,
) : Filter.Select<String>(name, options.map { it.label }.toTypedArray(), default),
    QueryFilter {
    override fun addToUrl(builder: HttpUrl.Builder) {
        val value = options[state].value
        if (value.isNotEmpty()) builder.addQueryParameter(param, value)
    }
}

class ListFilter(private val options: List<FilterOption>) : Filter.Select<String>("List", (listOf("Browse") + options.map { it.label }).toTypedArray()) {
    val path get() = if (state == 0) null else options[state - 1].value
}

class CheckBoxOption(name: String, val value: String) : Filter.CheckBox(name)

class CheckBoxGroup(
    name: String,
    private val param: String,
    options: List<FilterOption>,
) : Filter.Group<CheckBoxOption>(name, options.map { CheckBoxOption(it.label, it.value) }),
    QueryFilter {
    override fun addToUrl(builder: HttpUrl.Builder) {
        state.filter { it.state }.forEach { builder.addQueryParameter(param, it.value) }
    }
}
