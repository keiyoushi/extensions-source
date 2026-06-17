package eu.kanade.tachiyomi.extension.id.westmanga

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.HttpUrl

interface UrlFilter {
    fun addToUrl(url: HttpUrl.Builder)
}

abstract class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
    private val queryParameterName: String,
    defaultValue: String? = null,
) : Filter.Select<String>(
    name,
    options.map { it.first }.toTypedArray(),
    options.indexOfFirst { it.second == defaultValue }.takeIf { it != -1 } ?: 0,
),
    UrlFilter {
    private val selected get() = options[state].second

    override fun addToUrl(url: HttpUrl.Builder) {
        url.addQueryParameter(queryParameterName, selected)
    }
}

class CheckBoxFilter(name: String, val value: String) : Filter.CheckBox(name)

abstract class CheckBoxGroup(
    name: String,
    options: List<Pair<String, String>>,
    private val queryParameterName: String,
) : Filter.Group<CheckBoxFilter>(
    name,
    options.map { CheckBoxFilter(it.first, it.second) },
),
    UrlFilter {
    private val checked get() = state.filter { it.state }.map { it.value }

    override fun addToUrl(url: HttpUrl.Builder) {
        checked.forEach {
            if (it.isNotBlank()) {
                url.addQueryParameter(queryParameterName, it)
            }
        }
    }
}

class SortFilter(
    defaultValue: String? = null,
) : SelectFilter(
    name = "Order",
    options = listOf(
        "Default" to "Default",
        "A-Z" to "Az",
        "Z-A" to "Za",
        "Updated" to "Update",
        "Added" to "Added",
        "Popular" to "Popular",
    ),
    queryParameterName = "orderBy",
    defaultValue = defaultValue,
) {
    companion object {
        val popular = FilterList(SortFilter("Popular"))
        val latest = FilterList(SortFilter("Update"))
    }
}

class StatusFilter :
    SelectFilter(
        name = "Status",
        options = listOf(
            "All" to "All",
            "Ongoing" to "Ongoing",
            "Completed" to "Completed",
            "Hiatus" to "Hiatus",
        ),
        queryParameterName = "status",
    )

class CountryFilter :
    SelectFilter(
        name = "Country / Type",
        options = listOf(
            "All" to "All",
            "Japan / Manga" to "JP",
            "China / Manhua" to "CN",
            "Korea / Manhwa" to "KR",
        ),
        queryParameterName = "country",
    )

class ColorFilter :
    SelectFilter(
        name = "Color",
        options = listOf(
            "All" to "All",
            "Colored" to "Colored",
            "Uncolored" to "Uncolored",
        ),
        queryParameterName = "color",
    )

class GenreFilter(options: List<Pair<String, String>>) :
    CheckBoxGroup(
        name = "Genre",
        options = options,
        queryParameterName = "genre[]",
    )
