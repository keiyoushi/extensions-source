package eu.kanade.tachiyomi.extension.en.atsumaru

import eu.kanade.tachiyomi.source.model.Filter

internal class GenreFilter(genres: List<Genre>) :
    Filter.Group<Filter.CheckBox>(
        "Genres",
        genres.map { genre ->
            object : Filter.CheckBox(genre.name, false) {}
        },
    ) {
    val genreIds = genres.map { it.id }
}

internal class TypeFilter(types: List<Type>) :
    Filter.Group<Filter.CheckBox>(
        "Manga Type",
        types.map { type ->
            object : Filter.CheckBox(type.name, false) {}
        },
    ) {
    val ids = types.map { it.id }
}

internal class StatusFilter(statuses: List<Status>) :
    Filter.Group<Filter.CheckBox>(
        "Publishing Status",
        statuses.map { status ->
            object : Filter.CheckBox(status.name, false) {}
        },
    ) {
    val ids = statuses.map { it.id }
}

internal class YearFilter : Filter.Text("Year (e.g., 2024)")

internal class MinChaptersFilter : Filter.Text("Minimum Chapters")

internal class SortFilter :
    Filter.Sort(
        "Sort By",
        arrayOf("Title", "Popularity", "Trending", "Date Added", "Release Date"),
        Selection(0, true),
    ) {
    companion object {
        val VALUES = arrayOf("title", "", "trending", "createdAt", "released")
    }
}

internal data class Genre(val name: String, val id: String)

internal data class Type(val name: String, val id: String)

internal data class Status(val name: String, val id: String)
