package eu.kanade.tachiyomi.extension.en.reimanga

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter :
    Filter.Sort(
        name = "Sort",
        values = sortValues.map { it.first }.toTypedArray(),
        state = Selection(0, false),
    ) {
    val sort get() = sortValues[state?.index ?: 0].second
    val direction get() = if (state?.ascending ?: false) "asc" else "desc"
}

private val sortValues = listOf(
    "Latest Update" to "latest",
    "Newest" to "newest",
    "Most Viewed" to "viewed",
    "Top Rated" to "scored",
    "Title A-Z" to "title",
)

class StatusFilter :
    Filter.Select<String>(
        name = "Status",
        values = statusValues.map { it.first }.toTypedArray(),
    ) {
    val status get() = statusValues[state].second
}

private val statusValues = listOf(
    "All" to null,
    "Ongoing" to "ongoing",
    "Completed" to "completed",
)

class TriStateOption(name: String, val value: String, state: Int) : Filter.TriState(name, state)

open class TriStateFilter(
    name: String,
    options: List<TriStateOption>,
) : Filter.Group<TriStateOption>(
    name = name,
    state = options,
) {
    val included get() = state.filter { it.isIncluded() }.map { it.value }
    val excluded get() = state.filter { it.isExcluded() }.map { it.value }
}

class GenreFilter(options: List<TriStateOption>) : TriStateFilter("Genres", options)

class TagFilter(options: List<TriStateOption>) : TriStateFilter("Tags", options)
