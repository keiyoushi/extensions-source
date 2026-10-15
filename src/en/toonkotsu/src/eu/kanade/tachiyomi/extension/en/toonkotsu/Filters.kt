package eu.kanade.tachiyomi.extension.en.toonkotsu

import eu.kanade.tachiyomi.source.model.Filter

open class SelectFilter(
    name: String,
    val param: String,
    private val options: List<Pair<String, String>>,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val value get() = options[state].second
}

class SortFilter : Filter.Sort("Sort", SORTS.map { it.first }.toTypedArray(), Selection(0, false)) {
    val value get() = SORTS[state?.index ?: 0].second
    val order get() = if (state?.ascending == true) "asc" else "desc"
}

private val SORTS = listOf(
    "Relevance" to "relevance",
    "Popular" to "popular",
    "Latest upload" to "latest",
    "Recently added" to "new",
    "Rating" to "score",
    "Title" to "title",
    "Year" to "year",
    "Chapters" to "chapters",
)

class TypeFilter(types: List<String>) : SelectFilter("Type", "type", listOf("Any" to "") + types.map { it to it })

class StatusFilter :
    SelectFilter(
        "Status",
        "status",
        listOf("Any" to "", "Ongoing" to "Ongoing", "Complete" to "Complete", "Hiatus" to "Hiatus", "Canceled" to "Canceled"),
    )

class DemographicFilter : SelectFilter("Demographic", "demographic", listOf("Any" to "") + DEMOGRAPHICS.map { it to it })

class GenreTriState(name: String) : Filter.TriState(name)

class GenreFilter(genres: List<String>) : Filter.Group<GenreTriState>("Genres", genres.map(::GenreTriState))

val DEMOGRAPHICS = listOf("Shounen", "Shoujo", "Seinen", "Josei")
