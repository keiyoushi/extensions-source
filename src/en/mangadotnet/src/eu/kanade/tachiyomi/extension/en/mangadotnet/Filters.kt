package eu.kanade.tachiyomi.extension.en.mangadotnet

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter :
    Filter.Sort(
        name = "Sort",
        values = sortOrders.map { it.first }.toTypedArray(),
        state = Selection(0, false),
    ) {
    val sort get() = sortOrders[state?.index ?: 0].second
    val ascending get() = state?.ascending ?: false
}

private val sortOrders = listOf(
    "Relevance" to "relevance",
    "Alphabetical" to "Alphabetical",
    "Latest Update" to "latest",
    "Total Chapters" to "chapters",
    "Most Viewed" to "views",
    "Most Bookmarked" to "bookmarks",
    "Top Rated" to "rating",
)

class StatusFilter :
    Filter.Select<String>(
        name = "Status",
        values = status.map { it.first }.toTypedArray(),
    ) {
    val selected get() = status[state].second
}

private val status = listOf(
    "Any Status" to null,
    "Ongoing" to "Ongoing",
    "Completed" to "Completed",
)

class TypeFilter :
    Filter.Select<String>(
        name = "Type",
        values = types.map { it.first }.toTypedArray(),
    ) {
    val selected get() = types[state].second
}

private val types = listOf(
    "All" to null,
    "Manga" to "JP",
    "Manhwa" to "KR",
    "Manhua" to "CN",
    "One Shot" to "ONESHOT",
)

class CheckBoxFilter(name: String) : Filter.CheckBox(name)

class GenreFilter(genreValues: List<String>) :
    Filter.Group<CheckBoxFilter>(
        name = "Genre",
        state = genreValues.map { CheckBoxFilter(it) },
    ) {
    val checked get() = state.filter { it.state }.map { it.name }
}
