package eu.kanade.tachiyomi.extension.en.templescan

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

// The site's own option values; it lists one status at a time, Ongoing by default
val STATUSES = listOf("Ongoing", "Hiatus", "Completed", "Canceled", "Dropped")

private val ORDERS = listOf(
    "Update Chapter" to "update_chapter",
    "Created At" to "created_at",
    "Trending" to "total_views",
    "Title" to "Title",
)

// "All" lists each status in turn, each sorted on its own
class StatusFilter : Filter.Select<String>("Status", (STATUSES + "All").toTypedArray()) {
    val selected get() = STATUSES.getOrNull(state)?.let(::listOf) ?: STATUSES
}

class OrderFilter(default: String) :
    Filter.Sort(
        "Order by",
        ORDERS.map { it.first }.toTypedArray(),
        Selection(ORDERS.indexOfFirst { it.second == default }, false),
    ) {
    val selected get() = ORDERS[state?.index ?: 0].second
    val direction get() = if (state?.ascending == true) "ascendente" else "descendente"

    companion object {
        val POPULAR = FilterList(OrderFilter("total_views"))
        val LATEST = FilterList(OrderFilter("update_chapter"))
    }
}

fun getFilters() = FilterList(
    StatusFilter(),
    OrderFilter("total_views"),
)
