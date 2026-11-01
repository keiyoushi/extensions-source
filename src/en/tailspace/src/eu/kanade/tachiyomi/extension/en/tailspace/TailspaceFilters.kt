package eu.kanade.tachiyomi.extension.en.tailspace

import eu.kanade.tachiyomi.source.model.Filter

// Values are the labels lowercased with only the first space replaced, as the site does it
class SortFilter :
    Filter.Select<String>(
        "Sort by",
        SORTS.map { it.first }.toTypedArray(),
        1,
    ) {
    val value get() = SORTS[state].second

    companion object {
        private val SORTS = listOf(
            "Updated" to "",
            "Trending" to "trending",
            "Score (popularity)" to "score-(popularity)",
            "Average score (quality)" to "average-score (quality)",
            "Most pages" to "most-pages",
            "Fewest pages" to "fewest-pages",
            "Random" to "random",
        )
    }
}

class CategoryCheckBox(name: String) : Filter.CheckBox(name)

class CategoryGroup :
    Filter.Group<CategoryCheckBox>(
        "Categories",
        listOf("Male", "Female", "Intersex", "Mix").map(::CategoryCheckBox),
    )

class FinishedFilter : Filter.CheckBox("Finished only")

class SfwFilter : Filter.CheckBox("Safe-ish only")

class TagTriState(name: String, val id: Int) : Filter.TriState(name)

class TagGroup(name: String, tags: List<Tag>) :
    Filter.Group<TagTriState>(
        name,
        tags.sortedBy { it.name }.map { TagTriState(it.name, it.id) },
    )
