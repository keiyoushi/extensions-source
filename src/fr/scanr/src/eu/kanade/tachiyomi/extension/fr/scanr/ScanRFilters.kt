package eu.kanade.tachiyomi.extension.fr.scanr

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UriFilter {
    fun addToUri(builder: HttpUrl.Builder)
}

open class UriMultiSelectOption(name: String, val value: String, state: Boolean) : Filter.CheckBox(name, state)

open class UriMultiSelectFilter(
    name: String,
    private val param: String,
    options: List<UriMultiSelectOption>,
) : Filter.Group<UriMultiSelectOption>(
    name,
    options,
),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        val selected = state.filter { it.state }.map { it.value }
        if (selected.isNotEmpty()) {
            builder.addQueryParameter(param, selected.joinToString(","))
        }
    }
}

class TypeFilter :
    UriMultiSelectFilter(
        "Type",
        "type",
        listOf(
            UriMultiSelectOption("OS", "os", true),
            UriMultiSelectOption("Séries", "series", true),
        ),
    )

class StatusFilter :
    UriMultiSelectFilter(
        "Status",
        "status",
        listOf(
            UriMultiSelectOption("Terminé", "completed", true),
            UriMultiSelectOption("En cours", "ongoing", true),
        ),
    )

class AdultFilter :
    UriMultiSelectFilter(
        "Adulte ?",
        "adult",
        listOf(
            UriMultiSelectOption("+18", "18", true),
            UriMultiSelectOption("Normal", "normal", true),
        ),
    )
