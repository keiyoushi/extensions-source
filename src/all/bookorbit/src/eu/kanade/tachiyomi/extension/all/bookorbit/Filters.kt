package eu.kanade.tachiyomi.extension.all.bookorbit

import eu.kanade.tachiyomi.source.model.Filter

class LibraryFilter(private val libraries: List<LibraryDto>) : Filter.Select<String>("Library", (listOf("All") + libraries.map { it.name }).toTypedArray()) {
    val selectedId get() = libraries.getOrNull(state - 1)?.id
}

class SortFilter :
    Filter.Sort(
        "Sort",
        SORTS.map { it.first }.toTypedArray(),
        Selection(0, true),
    ) {
    val sort get() = SORTS[state?.index ?: 0].second
    val order get() = if (state?.ascending != false) "asc" else "desc"
}

private val SORTS = listOf(
    "Name" to "name",
    "Recently added" to "lastAddedAt",
    "Book count" to "bookCount",
    "Read progress" to "readProgress",
)
