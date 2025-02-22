package eu.kanade.tachiyomi.extension.en.mangabtt

import eu.kanade.tachiyomi.source.model.Filter

data class FilterOption(val displayName: String, val value: String)

inline fun <reified T> List<*>.firstInstanceOrNull() = firstOrNull { it is T } as? T

open class EnhancedSelect(name: String, private val _values: List<FilterOption>, state: Int = 0) :
    Filter.Select<String>(name, _values.map { it.displayName }.toTypedArray(), state) {

    val selectedValue: String?
        get() = _values.getOrNull(state)?.value
}

class SortByFilter(default: Int = 1) : EnhancedSelect(
    "Sort By",
    listOf(
        FilterOption("Top day", "13"),
        FilterOption("Top week", "12"),
        FilterOption("Top month", "11"),
        FilterOption("Top All", "10"),
        FilterOption("Comment", "25"),
        FilterOption("New Manga", "15"),
        FilterOption("Chapter", "30"),
        FilterOption("Latest Updates", "0"),
    ),
    default - 1,
)

class StatusFilter(default: Int = 1) : EnhancedSelect(
    "Status",
    listOf(
        FilterOption("All", "-1"),
        FilterOption("Completed", "2"),
        FilterOption("Ongoing", "1"),
    ),
    default - 1,
)

class GenreFilter(default: Int = 1) : EnhancedSelect(
    "Genre",
    listOf(
        FilterOption("All", ""),
        FilterOption("Action", "action"),
        FilterOption("ADVENTURE", "adventure"),
        FilterOption("Comedy", "comedy"),
        FilterOption("Cooking", "cooking"),
        FilterOption("Drama", "drama"),
        FilterOption("Fantasy", "fantasy"),
        FilterOption("Historical", "historical"),
        FilterOption("Horror", "horror"),
        FilterOption("Isekai", "isekai"),
        FilterOption("Josei", "josei"),
        FilterOption("Manhua", "manhua"),
        FilterOption("Manhwa", "manhwa"),
        FilterOption("Martial Arts", "martial-arts"),
        FilterOption("Mecha", "mecha"),
        FilterOption("MYSTERY", "mystery"),
        FilterOption("PSYCHOLOGICAL", "psychological"),
        FilterOption("Romance", "romance"),
        FilterOption("School Life", "school-life"),
        FilterOption("Sci fi", "sci-fi"),
        FilterOption("Seinen", "seinen"),
        FilterOption("Shoujo", "shoujo"),
        FilterOption("Shounen", "shounen"),
        FilterOption("SLICE OF LIF", "slice-of-lif"),
        FilterOption("Slice of Life", "slice-of-life"),
        FilterOption("Sports", "sports"),
        FilterOption("SUGGESTIVE", "suggestive"),
        FilterOption("SUPERNATURAL", "supernatural"),
        FilterOption("TRAGEDY", "tragedy"),
        FilterOption("Webtoons", "webtoons"),
    ),
    default - 1,
)
