package eu.kanade.tachiyomi.extension.en.aethonwebcomics

import eu.kanade.tachiyomi.source.model.Filter

class GenreFilter(private val genres: List<Genre>) :
    Filter.Select<String>(
        "Genre",
        (listOf("All") + genres.map { it.name }).toTypedArray(),
    ) {
    val id: Int? get() = genres.getOrNull(state - 1)?.id
}

class SortFilter :
    Filter.Select<String>(
        "Sort",
        SORTS.map { it.first }.toTypedArray(),
    ) {
    val orderBy: String get() = SORTS[state].second
    val order: String get() = SORTS[state].third

    private companion object {
        val SORTS = listOf(
            Triple("Oldest", "date", "asc"),
            Triple("Newest", "date", "desc"),
            Triple("Title", "title", "asc"),
        )
    }
}
