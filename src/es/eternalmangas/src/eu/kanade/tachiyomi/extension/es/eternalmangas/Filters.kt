package eu.kanade.tachiyomi.extension.es.eternalmangas

import eu.kanade.tachiyomi.source.model.Filter
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl

@Serializable
class FilterOption(val name: String, val value: String)

class SelectFilter(
    name: String,
    private val param: String,
    private val options: List<FilterOption>,
) : Filter.Select<String>(name, options.map { it.name }.toTypedArray()) {
    fun addTo(builder: HttpUrl.Builder) {
        val value = options[state].value
        if (value.isNotEmpty()) builder.addQueryParameter(param, value)
    }
}
