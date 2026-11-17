package eu.kanade.tachiyomi.extension.all.achymanga

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.HttpUrl

class AdultFilter : Filter.CheckBox("+18")

class SelectFilter(
    private val param: String,
    name: String,
    private val options: List<Pair<String, String>>,
) : Filter.Select<String>(name, options.map { it.second }.toTypedArray()) {
    fun addTo(url: HttpUrl.Builder) {
        options[state].first.takeIf { it.isNotEmpty() }?.let { url.addQueryParameter(param, it) }
    }
}

fun getFilters(selects: List<SelectDto>?) = FilterList(
    listOf(AdultFilter()) + selects.orEmpty().map { SelectFilter(it.name, it.label, it.options) },
)
