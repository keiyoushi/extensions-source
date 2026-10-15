package eu.kanade.tachiyomi.extension.en.emaqi

import eu.kanade.tachiyomi.source.model.Filter

class GenreModeFilter : Filter.Select<String>("Genre mode", arrayOf("AND", "OR")) {
    val isOr: Boolean
        get() = state == 1
}

class GenreFilter :
    Filter.Group<CheckBoxFilter>(
        "Genres",
        listOf(
            "Death Game" to "death-game",
            "Psychological" to "psychological",
            "Boys Love" to "boys-love",
            "Suspense" to "suspense",
            "Military" to "military",
            "Rom-Com" to "rom-com",
            "Mystery" to "mystery",
            "Adventure" to "adventure",
            "Drama" to "drama",
            "Slice of Life" to "slice-of-life",
            "Girls Love" to "girls-love",
            "Sports" to "sports",
            "Dark Drama" to "dark-drama",
            "Sci-Fi" to "sci-fi",
            "Isekai" to "isekai",
            "Action" to "action",
            "Fantasy" to "fantasy",
            "Horror" to "horror",
            "Romance" to "romance",
            "Comedy" to "comedy",
        ).map { CheckBoxFilter(it.first, it.second) },
    ) {
    val checked: List<String>
        get() = state.filter { it.state }.map { it.value }
}

class CheckBoxFilter(name: String, val value: String) : Filter.CheckBox(name)
