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
    "Alphabetical" to "alphabetical",
    "Latest Update" to "latest",
    "Total Chapters" to "chapters",
    "Most Viewed" to "views",
    "Most Tracked" to "tracked",
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
    "Hiatus" to "Hiatus",
)

class TypeCheckBox(name: String, val value: String) : Filter.CheckBox(name)

class TypeFilter :
    Filter.Group<TypeCheckBox>(
        "Type",
        types.map { TypeCheckBox(it.first, it.second) },
    ) {
    val checked get() = state.filter { it.state }.map { it.value }
}

private val types = listOf(
    "Manga" to "JP",
    "Manhwa" to "KR",
    "Manhua" to "CN",
    "One Shot" to "ONESHOT",
)

class TriStateFilter(name: String, val value: String = name, state: Int = STATE_IGNORE) : Filter.TriState(name, state)

class GenreFilter(genreValues: List<String>, excluded: Set<String>) :
    Filter.Group<TriStateFilter>(
        name = "Genre",
        state = genreValues.map { genre ->
            val state = if (genre in excluded) TriState.STATE_EXCLUDE else TriState.STATE_IGNORE
            TriStateFilter(genre, state = state)
        },
    ) {
    val included get() = state.filter { it.isIncluded() }.map { it.value }
    val excluded get() = state.filter { it.isExcluded() }.map { it.value }
}
