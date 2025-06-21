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
            url.addQueryParameter(queryParameterName, it)
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

class StatusFilter : SelectFilter(
    name = "Status",
    options = listOf(
        "All" to "All",
        "Ongoing" to "Ongoing",
        "Completed" to "Completed",
        "Hiatus" to "Hiatus",
    ),
    queryParameterName = "status",
)

class CountryFilter : SelectFilter(
    name = "Country",
    options = listOf(
        "All" to "All",
        "Japan" to "JP",
        "China" to "CN",
        "Korea" to "KR",
    ),
    queryParameterName = "country",
)

class ColorFilter : SelectFilter(
    name = "Color",
    options = listOf(
        "All" to "All",
        "Colored" to "Colored",
        "Uncolored" to "Uncolored",
    ),
    queryParameterName = "color",
)

class GenreFilter : CheckBoxGroup(
    name = "Genre",
    options = listOf(
        "4-Koma" to "344",
        "Action" to "13",
        "Adult" to "2279",
        "Adventure" to "4",
        "Anthology" to "1494",
        "Comedy" to "5",
        "Comedy. Ecchi" to "2028",
        "Cooking" to "54",
        "Crime" to "856",
        "Crossdressing" to "1306",
        "Demon" to "1318",
        "Demons" to "64",
        "Drama" to "6",
        "Ecchi" to "14",
        "Ecchi. Comedy" to "1837",
        "Fantasy" to "7",
        "Game" to "36",
        "Gender Bender" to "149",
        "Genderswap" to "157",
        "genre drama" to "1843",
        "Ghosts" to "1579",
        "Gore" to "56",
        "Gyaru" to "812",
        "Harem" to "17",
        "Historical" to "44",
        "Horror" to "211",
    ),
    queryParameterName = "genre[]",
)
