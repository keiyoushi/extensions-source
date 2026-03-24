package eu.kanade.tachiyomi.extension.en.mangacloud

import eu.kanade.tachiyomi.source.model.Filter

abstract class SelectFilter(
    name: String,
    private val options: List<Pair<String, String?>>,
) : Filter.Select<String?>(
    name,
    options.map { it.first }.toTypedArray(),
) {
    val selected get() = options[state].second
}

class TriStateFilter(name: String, val value: String) : Filter.TriState(name)

class TriStateGroupFilter(
    name: String,
    options: List<Pair<String, String>>,
) : Filter.Group<TriStateFilter>(
    name,
    options.map { TriStateFilter(it.first, it.second) },
) {
    val included get() = state.filter { it.isIncluded() }.map { it.value }
    val excluded get() = state.filter { it.isExcluded() }.map { it.value }
}

class TypeFilter :
    SelectFilter(
        name = "Type",
        options = listOf(
            "Any" to null,
            "Manga" to "Manga",
            "Manhua" to "Manhua",
            "Manhwa" to "Manhwa",
        ),
    )

class StatusFilter :
    SelectFilter(
        name = "Status",
        options = listOf(
            "Any" to null,
            "Ongoing" to "Ongoing",
            "Completed" to "Completed",
            "Cancelled" to "Cancelled",
            "Hiatus" to "Hiatus",
            "Unknown" to "Unknown",
        ),
    )

class SortFilter :
    SelectFilter(
        name = "Sort by",
        options = listOf(
            "None" to null,
            "Latest Upload" to "updated_date-DESC",
            "Oldest Upload" to "updated_date-ASC",
            "Title Ascending" to "title-ASC",
            "Title Descending" to "title-DESC",
            "Recently Added" to "created_date-DESC",
            "Oldest Added" to "created_date-ASC",
        ),
    )
