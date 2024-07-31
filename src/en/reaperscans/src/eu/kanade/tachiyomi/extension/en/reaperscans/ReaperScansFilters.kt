package eu.kanade.tachiyomi.extension.en.reaperscans

import eu.kanade.tachiyomi.source.model.Filter

abstract class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray(), 0) {
    val selected get() = options[state].second
}

class OrderFilter : SelectFilter(
    "Order by",
    listOf(
        Pair("Popularity", "total_views"),
        Pair("Created at", "created_at"),
        Pair("Updated at", "updated_at"),
        Pair("Latest", "latest"),
        Pair("Title", "title"),
    ),
)

class OrderBySortFilter : SelectFilter(
    "Order",
    listOf(
        Pair("Descending", "desc"),
        Pair("Ascending", "asc"),
    ),
)

class StatusFilter : SelectFilter(
    "Status",
    listOf(
        Pair("All", "All"),
        Pair("Hiatus", "Hiatus"),
        Pair("Completed", "Completed"),
        Pair("Ongoing", "Ongoing"),
        Pair("Dropped", "Dropped"),
    ),
)

class TagFilter(tagName: String, tagId: Int) : Filter.CheckBox(tagName, false) {
    val id = tagId
}
