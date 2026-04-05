package eu.kanade.tachiyomi.extension.en.ezmanga

import eu.kanade.tachiyomi.source.model.Filter

class EZmangaGenreFilter : Filter.Select<String>("Genre", DISPLAY) {
    val value get() = VALUES[state]
    companion object {
        private val ENTRIES = arrayOf(
            "All" to "", "Isekai" to "isekai", "Action" to "action", "Adventure" to "adventure",
            "All Ages" to "all-ages", "Comedy" to "comedy", "Drama" to "drama", "Fantasy" to "fantasy",
            "GORE" to "gore", "Game World" to "game-world", "Gender Bender" to "gender-bender",
            "Historical" to "historical", "Horror" to "horror", "Josei" to "josei", "Magic" to "magic",
            "Martial Arts" to "martial-arts", "Modern" to "modern", "Mystery" to "mystery",
            "Psychological" to "psychological", "Regression" to "regression", "Romance" to "romance",
            "Royalty" to "royalty", "School Life" to "school-life", "Science Fiction" to "science-fiction",
            "Slice of Life" to "slice-of-life", "Sports" to "sports", "Supernatural" to "supernatural",
            "Thriller" to "thriller", "Webtoon" to "webtoon", "Another World" to "another-world",
            "Crossdressing" to "crossdressing", "Dr" to "dr", "Elf" to "elf",
            "Female Cartoon" to "emale-cartoon", "Full Color" to "full-color",
            "Love Affair" to "love-affair", "Manhwa" to "manhwa", "Pure" to "pure",
            "Revenge" to "revenge", "Shoujo" to "shoujo", "White Cat" to "white-cat",
        )
        val DISPLAY = ENTRIES.map { it.first }.toTypedArray()
        val VALUES = ENTRIES.map { it.second }.toTypedArray()
    }
}
