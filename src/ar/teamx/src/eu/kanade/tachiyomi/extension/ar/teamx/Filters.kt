package eu.kanade.tachiyomi.extension.ar.teamx

import eu.kanade.tachiyomi.source.model.Filter
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl

@Serializable
class FilterOption(
    val name: String,
    val value: String,
)

typealias Options = List<FilterOption>

@Serializable
class FilterData(
    val genres: Options,
    val types: Options,
    val states: Options,
)

interface UrlPartFilter {
    fun addUrlParameter(url: HttpUrl.Builder)
}

abstract class SelectFilter(
    name: String,
    private val urlParameter: String,
    private val options: Options,
) : Filter.Select<String>(
    name,
    options.map { it.name }.toTypedArray(),
),
    UrlPartFilter {
    override fun addUrlParameter(url: HttpUrl.Builder) {
        val value = options[state].value
        if (value.isNotEmpty()) {
            url.addQueryParameter(urlParameter, value)
        }
    }
}

class StatusFilter(
    options: Options,
) : SelectFilter("الحالة", "status", options)

class TypeFilter(
    options: Options,
) : SelectFilter("النوع", "type", options)

class GenreFilter(
    options: Options,
) : SelectFilter("التصنيفات", "genre", options)
