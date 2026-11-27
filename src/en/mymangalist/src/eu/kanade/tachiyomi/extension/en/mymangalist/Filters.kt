package eu.kanade.tachiyomi.extension.en.mymangalist

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.HttpUrl

fun getFilters(selects: List<SelectData>?): FilterList = FilterList(
    selects.orEmpty().map { SelectFilter(it.label, it.parameter, it.options, it.default) },
)

class SelectFilter(
    name: String,
    private val parameter: String,
    private val options: List<Pair<String, String>>,
    default: Int,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray(), default) {
    fun addTo(builder: HttpUrl.Builder) {
        val value = options[state].second
        if (value.isNotEmpty()) builder.addQueryParameter(parameter, value)
    }
}
