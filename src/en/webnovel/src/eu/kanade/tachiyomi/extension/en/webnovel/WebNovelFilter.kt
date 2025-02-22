package eu.kanade.tachiyomi.extension.en.webnovel

import eu.kanade.tachiyomi.source.model.Filter

data class FilterOption(val displayName: String, val value: String)

open class EnhancedSelect(name: String, private val _values: List<FilterOption>, state: Int = 0) :
    Filter.Select<String>(name, _values.map { it.displayName }.toTypedArray(), state) {

    val selectedValue: String?
        get() = _values.getOrNull(state)?.value
}

class SortByFilter(default: Int = 1) : EnhancedSelect(
    "Sort By",
    listOf(
        FilterOption("Popular", "1"),
        FilterOption("Recommended", "2"),
        FilterOption("Most collections", "3"),
        FilterOption("Rating", "4"),
        FilterOption("Time updated", "5"),
    ),
    default - 1,
)

class ContentStatusFilter : EnhancedSelect(
    "Content status",
    listOf(
        FilterOption("All", "0"),
        FilterOption("Ongoing", "1"),
        FilterOption("Completed", "2"),
    ),
)

class GenreFilter : EnhancedSelect(
    "Genre",
    listOf(
        FilterOption("All", "0"),
        FilterOption("Action", "60002"),
        FilterOption("Adventure", "60014"),
        FilterOption("Comedy", "60011"),
        FilterOption("Cooking", "60009"),
        FilterOption("Diabolical", "60027"),
        FilterOption("Drama", "60024"),
        FilterOption("Eastern", "60006"),
        FilterOption("Fantasy", "60022"),
        FilterOption("Harem", "60017"),
        FilterOption("History", "60018"),
        FilterOption("Horror", "60015"),
        FilterOption("Inspiring", "60013"),
        FilterOption("LGBT+", "60029"),
        FilterOption("Magic", "60016"),
        FilterOption("Mystery", "60008"),
        FilterOption("Romance", "60003"),
        FilterOption("School", "60007"),
        FilterOption("Sci-fi", "60004"),
        FilterOption("Slice of Life", "60019"),
        FilterOption("Sports", "60023"),
        FilterOption("Transmigration", "60012"),
        FilterOption("Urban", "60005"),
        FilterOption("Wuxia", "60010"),
    ),
)
