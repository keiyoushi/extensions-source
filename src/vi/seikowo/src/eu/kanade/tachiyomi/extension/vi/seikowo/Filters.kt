package eu.kanade.tachiyomi.extension.vi.seikowo

import eu.kanade.tachiyomi.source.model.Filter

private class Option(
    val displayName: String,
    val value: String?,
)

private val statusOptions = arrayOf(
    Option("All Status", null),
    Option("Completed", "Status_Completed"),
    Option("Ongoing", "Status_Ongoing"),
)

private val sortOptions = arrayOf(
    Option("Latest Updates", "updated"),
    Option("Recently Added", "published"),
    Option("Title A-Z", "title"),
    Option("Most Comments", "popular"),
)

class StatusFilter :
    Filter.Select<String>(
        "Status",
        statusOptions.map { it.displayName }.toTypedArray(),
    ) {
    val selectedValue: String?
        get() = statusOptions[state].value
}

class SortByFilter :
    Filter.Select<String>(
        "Sort By",
        sortOptions.map { it.displayName }.toTypedArray(),
    ) {
    val selectedValue: String
        get() = sortOptions[state].value ?: "updated"
}

class GenreFilter(genres: List<String>) :
    Filter.Select<String>(
        "Genres",
        arrayOf("All Genres", *genres.toTypedArray()),
    ) {
    val selectedValue: String?
        get() = genreValues[state]

    private val genreValues = listOf(null) + genres
}
