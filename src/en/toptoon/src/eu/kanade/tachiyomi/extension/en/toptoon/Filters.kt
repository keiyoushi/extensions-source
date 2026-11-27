package eu.kanade.tachiyomi.extension.en.toptoon

import eu.kanade.tachiyomi.source.model.Filter

enum class SortOrder(val label: String) {
    POPULAR("Best selling"),
    VIEWS("Most viewed"),
    LATEST("Latest update"),
}

class SortFilter : Filter.Select<String>("Sort by", SortOrder.entries.map { it.label }.toTypedArray()) {
    val sort get() = SortOrder.entries[state]
}

class GenreFilter(private val genres: List<String>) : Filter.Select<String>("Genre", (listOf("All") + genres).toTypedArray()) {
    val genre get() = genres.getOrNull(state - 1)
}

class StatusFilter : Filter.Select<String>("Status", arrayOf("All", "Ongoing", "Completed")) {
    val completed get() = if (state == 0) null else state == 2
}

class DayFilter : Filter.Select<String>("Update day", arrayOf("All") + DAYS.map { it.second }.toTypedArray()) {
    val day get() = DAYS.getOrNull(state - 1)?.first
}

private val DAYS = listOf(
    "mon" to "Monday",
    "tue" to "Tuesday",
    "wed" to "Wednesday",
    "thu" to "Thursday",
    "fri" to "Friday",
    "sat" to "Saturday",
    "sun" to "Sunday",
)
