package eu.kanade.tachiyomi.extension.fr.scanr

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UriFilter {
    fun addToUri(builder: HttpUrl.Builder)
}

open class UriMultiSelectOption(name: String, val value: String) : Filter.CheckBox(name)

open class UriMultiSelectFilter(
    name: String,
    private val param: String,
    private val options: Array<Pair<String, String>>,
) : UriFilter, Filter.Group<UriMultiSelectOption>(
    name,
    options.map { UriMultiSelectOption(it.first, it.second).apply { state = true } },
) {
    override fun addToUri(builder: HttpUrl.Builder) {
        val selected = state.filter { it.state }.map { it.value }
        if (selected.isNotEmpty()) {
            builder.addQueryParameter(param, selected.joinToString(","))
        }
    }
}

class TypeFilter : UriMultiSelectFilter(
    "Type",
    "type",
    arrayOf(
        Pair("OS", "os"),
        Pair("Séries", "series"),
    ),
)

class StatusFilter : UriMultiSelectFilter(
    "Status",
    "status",
    arrayOf(
        Pair("Terminé", "completed"),
        Pair("En cours", "ongoing"),
    ),
)
