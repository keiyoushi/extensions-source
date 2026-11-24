package eu.kanade.tachiyomi.extension.tr.seitoon

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.HttpUrl

class SelectFilter(
    private val key: String,
    name: String,
    private val options: List<OptionDto>,
    state: Int = 0,
) : Filter.Select<String>(name, options.map { it.label }.toTypedArray(), state) {
    fun addTo(url: HttpUrl.Builder) {
        options[state].value.takeIf { it.isNotEmpty() }?.let { url.addQueryParameter(key, it) }
    }
}

// The filters of the site's catalogue page, the sort defaulting to the popular list
fun getFilters(filters: List<FilterDto>?): FilterList = FilterList(
    filters.orEmpty().map { filter ->
        val default = if (filter.name == "sort") filter.options.indexOfFirst { it.value == "popular" }.coerceAtLeast(0) else 0
        SelectFilter(filter.name, filter.label, filter.options, default)
    },
)
