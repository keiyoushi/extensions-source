package eu.kanade.tachiyomi.extension.id.siimanga

import eu.kanade.tachiyomi.source.model.Filter

open class SelectFilter(
    name: String,
    val param: String,
    private val options: List<Pair<String, String>>,
    default: String = "",
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray(), options.indexOfFirst { it.second == default }) {
    val value get() = options[state].second
}

class SortFilter(default: String = "popular") :
    SelectFilter(
        "Sort by",
        "sort",
        listOf(
            "Latest update" to "latest",
            "Popular" to "popular",
            "Rating" to "rating",
            "Newly added" to "new",
            "Title A-Z" to "title",
        ),
        default,
    )

class StatusFilter :
    SelectFilter(
        "Status",
        "status",
        listOf(
            "All" to "",
            "Ongoing" to "ONGOING",
            "Completed" to "COMPLETED",
            "Hiatus" to "HIATUS",
        ),
    )

class TypeFilter :
    SelectFilter(
        "Type",
        "type",
        listOf(
            "All" to "",
            "Manga" to "MANGA",
            "Manhwa" to "MANHWA",
            "Manhua" to "MANHUA",
        ),
    )

class GenreCheckBox(name: String, val slug: String) : Filter.CheckBox(name)

class GenreFilter(genres: List<GenreDto>) : Filter.Group<GenreCheckBox>("Genres", genres.map { GenreCheckBox(it.name, it.slug) }) {
    val selected get() = state.filter { it.state }.map { it.slug }
}
