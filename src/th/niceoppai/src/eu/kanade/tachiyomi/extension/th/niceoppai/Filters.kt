package eu.kanade.tachiyomi.extension.th.niceoppai

import eu.kanade.tachiyomi.source.model.Filter

const val ORDER_BY_FILTER_TITLE = "Order By เรียกตาม"
val ORDER_BY_FILTER_OPTIONS = arrayOf("Name (A-Z)", "Name (Z-A)", "Last Updated", "Oldest Updated", "Most Popular", "Most Popular (Weekly)", "Most Popular (Monthly)", "Least Popular", "Last Added", "Early Added", "Top Rating", "Lowest Rating")
val ORDER_BY_FILTER_OPTIONS_VALUES = arrayOf("name-az", "name-za", "last-updated", "oldest-updated", "most-popular", "most-popular-weekly", "most-popular-monthly", "least-popular", "last-added", "early-added", "top-rating", "lowest-rating")

open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>, state: Int = 0) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), state) {
    fun toUriPart() = vals[state].second
}

class OrderByFilter(title: String, options: List<Pair<String, String>>, state: Int = 0) : UriPartFilter(title, options.toTypedArray(), state)
