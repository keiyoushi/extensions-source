package eu.kanade.tachiyomi.extension.en.tailspace

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter :
    Filter.Select<String>(
        "Sort by",
        arrayOf(
            "Updated",
            "Trending",
            "Score (popularity)",
            "Average score (quality)",
            "Random",
        ),
    ) {
    val selectedValue: String
        get() = when (values[state]) {
            "Trending" -> "trending"
            "Score (popularity)" -> "score-(popularity)"
            "Average score (quality)" -> "average-score-(quality)"
            "Random" -> "random"
            else -> ""
        }
}

class CategoryCheckBox(val value: String) : Filter.CheckBox(value)

class CategoryGroup :
    Filter.Group<CategoryCheckBox>(
        "Categories",
        listOf(
            CategoryCheckBox("Male"),
            CategoryCheckBox("Female"),
            CategoryCheckBox("Intersex"),
            CategoryCheckBox("Mix"),
        ),
    )

class StatusFilter : Filter.CheckBox("Finished only")

class SfwFilter : Filter.CheckBox("Safe-ish only")

class TagTriState(name: String, val id: Int) : Filter.TriState(name)

class TagGroup(name: String, tags: List<TagDto>) :
    Filter.Group<TagTriState>(
        name,
        tags.map { TagTriState(it.name, it.id) },
    )
