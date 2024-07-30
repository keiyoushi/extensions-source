package eu.kanade.tachiyomi.extension.en.templescan

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

abstract class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
    defaultValue: String? = null,
) : Filter.Select<String>(
    name,
    options.map { it.first }.toTypedArray(),
    options.indexOfFirst { it.second == defaultValue }.coerceAtLeast(0),
) {
    val selected get() = options[state].second.takeUnless { it.isBlank() }
}

class StatusFilter : SelectFilter(
    "Status",
    listOf(
        "",
        "Ongoing",
        "Hiatus",
        "Completed",
        "Canceled",
        "Dropped",
    ).map { it to it },
)

class OrderFilter(default: String? = null) : SelectFilter(
    "Order by",
    listOf(
        "Update Chapter" to "updated",
        "Created At" to "created",
        "Trending" to "views",
    ),
    default,
) {
    companion object {
        val POPULAR = FilterList(OrderFilter("views"))
        val LATEST = FilterList(OrderFilter("updated"))
    }
}

fun getFilters() = FilterList(
    StatusFilter(),
    OrderFilter(),
)
