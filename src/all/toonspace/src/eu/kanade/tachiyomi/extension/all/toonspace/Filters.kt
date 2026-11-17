package eu.kanade.tachiyomi.extension.all.toonspace

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters() = FilterList(
    SortFilter(),
    StatusFilter(),
    GenreFilter(),
)

open class SelectFilter(
    name: String,
    private val options: List<Pair<String, String?>>,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val value get() = options[state].second
}

class SortFilter :
    SelectFilter(
        "Sort by",
        listOf(
            "Popular" to "viewsCount:desc",
            "New" to "releasedAt:desc",
        ),
    )

class StatusFilter :
    SelectFilter(
        "Status",
        listOf(
            "All" to null,
            "Ongoing" to "false",
            "Completed" to "true",
        ),
    )

class GenreFilter : SelectFilter("Genre", listOf("All" to null) + GENRES.map { (type, name) -> name to type })

// The site's categories, by their API type
val GENRES = mapOf(
    "romance" to "Romance",
    "fantasy" to "Fantasy",
    "action" to "Action",
    "drama" to "Drama",
    "thriller" to "Thriller",
    "sci-fi" to "Sci-Fi",
    "mystery" to "Mystery",
    "horror" to "Horror",
    "supernatural" to "Supernatural",
    "superhero" to "Superhero",
    "comedy" to "Comedy",
    "boys-love" to "BL (Boys' Love)",
    "girls-love" to "GL (Girls' Love)",
)
