package eu.kanade.tachiyomi.extension.en.manhwah

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter :
    Filter.Select<String>(
        "Sort by",
        SORTS.map { it.first }.toTypedArray(),
    ) {
    val value get() = SORTS[state].second

    companion object {
        private val SORTS = listOf(
            "Most Popular" to "popular",
            "Latest Updates" to "latest",
            "Recently Added" to "new",
            "Highest Rating" to "rating",
            "A - Z (Alphabetical)" to "az",
        )
    }
}

class GenreCheckBox(name: String, val id: String) : Filter.CheckBox(name)

class GenreFilter(genres: List<GenreDto>) : Filter.Group<GenreCheckBox>("Genres", genres.map { GenreCheckBox(it.name, it.id) })
