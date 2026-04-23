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

private val genreOptions = arrayOf(
    Option("All Genres", null),
    Option("Adaptation", "Adaptation"),
    Option("Adventure", "Adventure"),
    Option("Animals", "Animals"),
    Option("Crossdressing", "Crossdressing"),
    Option("Delinquents", "Delinquents"),
    Option("Genderswap", "Genderswap"),
    Option("Ghosts", "Ghosts"),
    Option("Monster Girls", "Monster Girls"),
    Option("Ninja", "Ninja"),
    Option("Office Workers", "Office Workers"),
    Option("Psychological", "Psychological"),
    Option("Reincarnation", "Reincarnation"),
    Option("Romance", "Romance"),
    Option("Survival", "Survival"),
    Option("Thriller", "Thriller"),
    Option("Time Travel", "Time Travel"),
    Option("action", "action"),
    Option("aliens", "aliens"),
    Option("comedy", "comedy"),
    Option("drama", "drama"),
    Option("fantasy", "fantasy"),
    Option("harem", "harem"),
    Option("horror", "horror"),
    Option("isekai", "isekai"),
    Option("manga", "manga"),
    Option("martial arts", "martial arts"),
    Option("monsters", "monsters"),
    Option("mystery", "mystery"),
    Option("school life", "school life"),
    Option("slice of life", "slice of life"),
    Option("supernatural", "supernatural"),
    Option("tragedy", "tragedy"),
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

class GenreFilter :
    Filter.Select<String>(
        "Genres",
        genreOptions.map { it.displayName }.toTypedArray(),
    ) {
    val selectedValue: String?
        get() = genreOptions[state].value
}
