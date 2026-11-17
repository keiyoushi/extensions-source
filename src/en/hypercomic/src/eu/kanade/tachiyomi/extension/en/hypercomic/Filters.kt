package eu.kanade.tachiyomi.extension.en.hypercomic

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

class DayFilter : Filter.Select<String>("Day", DAYS.map { it.first }.toTypedArray()) {
    val weekday: Int get() = DAYS[state].second

    companion object {
        private val DAYS = listOf(
            "All" to 7,
            "Monday" to 1,
            "Tuesday" to 2,
            "Wednesday" to 3,
            "Thursday" to 4,
            "Friday" to 5,
        )
    }
}

fun getFilters() = FilterList(
    Filter.Header("Text search ignores the day"),
    DayFilter(),
)
