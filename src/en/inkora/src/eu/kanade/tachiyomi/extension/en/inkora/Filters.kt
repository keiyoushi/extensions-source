package eu.kanade.tachiyomi.extension.en.inkora

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter :
    Filter.Select<String>(
        "Sort by",
        SORTS.map { it.first }.toTypedArray(),
        SORTS.indexOfFirst { it.second == "follows" },
    ) {
    val selected get() = SORTS[state].second
}

private val SORTS = listOf(
    "Most relevant" to "relevance",
    "Latest update" to "updated",
    "Most popular" to "follows",
    "Highest rated" to "rating",
    "Newest added" to "created",
    "Release year" to "year",
    "Trending 7d" to "views_7d",
    "Trending 1mo" to "views_1mo",
)

class CheckBoxOption(name: String, val value: String) : Filter.CheckBox(name)

open class CheckBoxGroup(name: String, options: List<Pair<String, String>>) : Filter.Group<CheckBoxOption>(name, options.map { CheckBoxOption(it.first, it.second) }) {
    val checked get() = state.filter { it.state }.joinToString(",") { it.value }.ifEmpty { null }
}

class TypeFilter :
    CheckBoxGroup(
        "Type",
        listOf("Manhwa" to "manhwa", "Manhua" to "manhua", "Manga" to "manga", "Other" to "other"),
    )

class StatusFilter :
    CheckBoxGroup(
        "Status",
        listOf("Releasing" to "releasing", "Completed" to "finished", "Hiatus" to "on_hiatus", "Dropped" to "discontinued"),
    )

class DemographicFilter :
    CheckBoxGroup(
        "Demographic",
        listOf("Josei" to "josei", "Seinen" to "seinen", "Shoujo" to "shoujo", "Shounen" to "shounen"),
    )

class GenreFilter(genres: List<GenreDto>) : CheckBoxGroup("Genres (all must match)", genres.map { it.name to it.slug })
