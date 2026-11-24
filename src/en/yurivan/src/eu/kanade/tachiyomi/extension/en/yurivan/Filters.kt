package eu.kanade.tachiyomi.extension.en.yurivan

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter :
    Filter.Select<String>(
        "Sort by",
        SORT_OPTIONS.map { it.first }.toTypedArray(),
    ) {
    val value get() = SORT_OPTIONS[state].second
}

private val SORT_OPTIONS = listOf(
    "Default" to null,
    "Relevance" to "relevance",
    "Latest updates" to "fresh",
    "Newest" to "new",
    "Most viewed" to "top",
    "Top this week" to "top7d",
    "Top this month" to "top30d",
    "Top rated" to "top_rated",
    "Oldest" to "oldest",
)

class Tag(name: String, val slug: String) : Filter.TriState(name)

class TagFilter(tags: List<Tag>) : Filter.Group<Tag>("Tags", tags) {
    val included get() = state.filter { it.isIncluded() }.map { it.slug }
    val excluded get() = state.filter { it.isExcluded() }.map { it.slug }
}
