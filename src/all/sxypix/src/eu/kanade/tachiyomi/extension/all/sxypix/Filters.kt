package eu.kanade.tachiyomi.extension.all.sxypix

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(name: String, private val options: List<Pair<String, String>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val value get() = options[state].second
}

class SortFilter : UriPartFilter("Sort", listOf("Trend" to "trend", "Latest" to "latest", "Likes" to "likes", "Views" to "views"))

class PeriodFilter : UriPartFilter("Period (ignored with text search)", listOf("Week" to "week", "All time" to "all", "Day" to "day", "Month" to "month"))
