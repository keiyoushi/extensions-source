package eu.kanade.tachiyomi.extension.all.utifa

import eu.kanade.tachiyomi.source.model.Filter

internal class ThemeFilter :
    SelectFilter(
        "Theme",
        listOf(
            Option("All", ""),
            Option("Hot blooded", "hot_blooded"),
            Option("Romance", "romance"),
            Option("School", "school"),
            Option("Adventure", "adventure"),
            Option("Sci-fi", "sci-fi"),
            Option("Slice of life", "slice_of_life"),
            Option("Mystery", "mystery"),
            Option("Sports", "sports"),
        ),
    )

internal class GradeFilter :
    SelectFilter(
        "Rating",
        listOf(
            Option("All", ""),
            Option("G", "G"),
            Option("PG", "PG"),
            Option("R", "R"),
        ),
    )

internal class UpdateStatusFilter :
    SelectFilter(
        "Status",
        listOf(
            Option("All", ""),
            Option("Ongoing", "0"),
            Option("Completed", "1"),
        ),
    )

internal class UtifaSortFilter : Filter.Sort(
    "Sort",
    arrayOf("Latest update", "Most viewed", "Most liked", "Created", "Title"),
    Selection(0, false),
)

internal open class SelectFilter(
    name: String,
    private val options: List<Option>,
) : Filter.Select<String>(name, options.map(Option::name).toTypedArray()) {
    fun selectedValue(): String = options[state].value
}

internal class Option(val name: String, val value: String)
