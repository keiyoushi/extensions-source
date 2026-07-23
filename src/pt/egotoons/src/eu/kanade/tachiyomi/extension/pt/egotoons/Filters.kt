package eu.kanade.tachiyomi.extension.pt.egotoons

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UrlFilter {
    fun addToUrl(builder: HttpUrl.Builder)
}

open class SelectFilter(
    name: String,
    private val parameter: String,
    private val options: List<Pair<String, String>>,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()),
    UrlFilter {
    val selectedValue get() = options[state].second

    override fun addToUrl(builder: HttpUrl.Builder) {
        selectedValue.takeIf(String::isNotEmpty)?.let { builder.addQueryParameter(parameter, it) }
    }
}

class SortFilter :
    SelectFilter(
        "Ordenar por",
        "ordenarPor",
        listOf(
            "Mais recentes" to "recentes",
            "Mais populares" to "populares",
            "A-Z" to "alfabetica",
        ),
    )

class FormatFilter(options: List<FilterOptionDto>) :
    SelectFilter(
        "Formato",
        "formato_id",
        listOf("Todos" to "") + options.map { it.name to it.id.toString() },
    )

class StatusFilter(options: List<FilterOptionDto>) :
    SelectFilter(
        "Status",
        "status_id",
        listOf("Todos" to "") + options.map { it.name to it.id.toString() },
    )

class TagFilter(options: List<FilterOptionDto>) :
    Filter.Group<TagCheckBox>("Tags", options.map { TagCheckBox(it.name, it.id.toString()) }),
    UrlFilter {
    override fun addToUrl(builder: HttpUrl.Builder) {
        state.filter { it.state }
            .joinToString(",") { it.value }
            .takeIf(String::isNotEmpty)
            ?.let { builder.addQueryParameter("tag_ids", it) }
    }
}

class TagCheckBox(name: String, val value: String) : Filter.CheckBox(name)
