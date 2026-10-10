package eu.kanade.tachiyomi.multisrc.keyoapp

import eu.kanade.tachiyomi.source.model.Filter

class StatusSelectFilter :
    Filter.Select<String>(
        "Status",
        ENTRIES.map { it.first }.toTypedArray(),
    ) {
    val selected: String get() = ENTRIES[state].second

    companion object {
        private val ENTRIES = arrayOf(
            "All" to "",
            "Ongoing" to "ongoing",
            "Completed" to "completed",
        )
    }
}

class SortFilter :
    Filter.Select<String>(
        "Sort by",
        ENTRIES.map { it.first }.toTypedArray(),
    ) {
    val selected: String get() = ENTRIES[state].second

    companion object {
        private val ENTRIES = arrayOf(
            "Latest" to "latest",
            "Trending" to "trending",
            "Popular" to "popular",
            "Most Viewed" to "views",
            "Top Rated" to "rating",
            "A-Z" to "az",
            "Z-A" to "za",
        )
    }
}

class TypeSelectFilter :
    Filter.Select<String>(
        "Type",
        ENTRIES.map { it.first }.toTypedArray(),
    ) {
    val selected: String get() = ENTRIES[state].second

    companion object {
        private val ENTRIES = arrayOf(
            "All" to "",
            "Manhwa" to "manhwa",
            "Manhua" to "manhua",
            "Mangatoon" to "mangatoon",
            "Manga" to "manga",
            "Novel" to "novel",
        )
    }
}

class GenreCheckBox(name: String, val value: String) : Filter.CheckBox(name)

class GenreTagFilter(genres: List<String>) :
    Filter.Group<GenreCheckBox>(
        "Genres",
        genres.map { GenreCheckBox(it.replaceFirstChar(Char::uppercase), it) },
    )
