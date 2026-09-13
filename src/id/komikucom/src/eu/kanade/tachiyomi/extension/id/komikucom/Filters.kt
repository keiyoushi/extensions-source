package eu.kanade.tachiyomi.extension.id.komikucom

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UriQueryFilter {
    fun addToQuery(builder: HttpUrl.Builder)
}

open class UriPartFilter(
    name: String,
    private val field: String,
    private val vals: Array<Pair<String, String>>,
    private val default: String = "",
) : Filter.Select<String>(
    name,
    vals.map { it.first }.toTypedArray(),
    vals.indexOfFirst { it.second == default }.takeIf { it != -1 } ?: 0,
),
    UriQueryFilter {
    override fun addToQuery(builder: HttpUrl.Builder) {
        if (vals.isEmpty()) return
        val selected = vals[state].second
        if (selected.isNotEmpty()) {
            builder.addQueryParameter(field, selected)
        }
    }
}

open class UriMultiSelectOption(name: String, val value: String) : Filter.CheckBox(name)

open class UriMultiSelectFilter(
    name: String,
    private val field: String,
    private val options: Array<Pair<String, String>>,
) : Filter.Group<UriMultiSelectOption>(
    name,
    options.map { UriMultiSelectOption(it.first, it.second) },
),
    UriQueryFilter {
    override fun addToQuery(builder: HttpUrl.Builder) {
        state.filter { it.state }.forEach {
            builder.addQueryParameter(field, it.value)
        }
    }
}

class SortFilter(sorts: Array<Pair<String, String>>) :
    UriPartFilter(
        "Sort",
        "sort",
        sorts,
        default = "update",
    )

class GenreFilter(genres: Array<Pair<String, String>>) :
    UriMultiSelectFilter(
        "Genre",
        "genres",
        genres,
    )

class StatusFilter(statuses: Array<Pair<String, String>>) :
    UriPartFilter(
        "Status",
        "status",
        statuses,
    )

class TypeFilter(types: Array<Pair<String, String>>) :
    UriPartFilter(
        "Type",
        "type",
        types,
    )
