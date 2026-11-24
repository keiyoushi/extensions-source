package eu.kanade.tachiyomi.extension.en.pornhwame

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(): FilterList = FilterList(
    Filter.Header("Ignored with a text search"),
    ListingFilter(),
)

class ListingFilter : Filter.Select<String>("List", LISTINGS.map { it.first }.toTypedArray()) {
    val path get() = LISTINGS[state].second
}

private val LISTINGS = listOf(
    "Popular" to "trending",
    "Latest update" to "latest-update",
    "Just added" to "new-collections",
    "All comics (A-Z)" to "lists",
)
