package eu.kanade.tachiyomi.extension.all.hitomi

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

typealias OrderType = Pair<String?, String>
typealias ParsedFilter = Pair<String, OrderType>

private fun parseFilter(query: StringBuilder, area: String, filterState: String) {
    filterState
        .trim()
        .split(',')
        .filter { it.isNotBlank() }
        .forEach {
            val trimmed = it.trim()
            val negativePrefix = if (trimmed.startsWith("-")) "-" else ""
            query.append(" $negativePrefix$area:${trimmed.removePrefix("-").replace(" ", "_")}")
        }
}

fun parseFilters(filters: FilterList): ParsedFilter {
    val query = StringBuilder()
    var order: OrderType = Pair("date", "added")
    filters.forEach { filter ->
        when (filter) {
            is SortFilter -> {
                order = filter.getOrder
            }
            is AreaFilter -> {
                parseFilter(query, filter.getAreaName, filter.state)
            }
            else -> { /* Do Nothing */ }
        }
    }
    return Pair(query.toString(), order)
}

private class OrderFilter(val name: String, val order: OrderType) {
    val getFilterName: String
        get() = name
    val getOrder: OrderType
        get() = order
}

private class SortFilter : UriPartFilter(
    "Sort By",
    arrayOf(
        OrderFilter("Date Added", Pair(null, "index")),
        OrderFilter("Date Published", Pair("date", "published")),
        OrderFilter("Popular: Today", Pair("popular", "today")),
        OrderFilter("Popular: Week", Pair("popular", "week")),
        OrderFilter("Popular: Month", Pair("popular", "month")),
        OrderFilter("Popular: Year", Pair("popular", "year")),
    ),
)

private open class UriPartFilter(displayName: String, val vals: Array<OrderFilter>) :
    Filter.Select<String>(displayName, vals.map { it.getFilterName }.toTypedArray()) {
    val getOrder: OrderType
        get() = vals[state].getOrder
}

private class AreaFilter(displayName: String, val areaName: String) :
    Filter.Text(displayName) {
    val getAreaName: String
        get() = areaName
}

fun getFilterListInternal(): FilterList = FilterList(
    SortFilter(),
    Filter.Header("Separate tags with commas (,)"),
    Filter.Header("Prepend with dash (-) to exclude"),
    AreaFilter("Artist(s)", "artist"),
    AreaFilter("Character(s)", "character"),
    AreaFilter("Group(s)", "group"),
    AreaFilter("Series", "series"),
    AreaFilter("Female Tag(s)", "female"),
    AreaFilter("Male Tag(s)", "male"),
    Filter.Header("Don't put Female/Male tags here, they won't work!"),
    AreaFilter("Tag(s)", "tag"),
)
