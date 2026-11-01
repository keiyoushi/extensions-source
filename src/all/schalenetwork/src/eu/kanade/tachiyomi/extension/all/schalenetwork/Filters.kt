package eu.kanade.tachiyomi.extension.all.schalenetwork

import eu.kanade.tachiyomi.source.model.Filter

class TextFilter(name: String, val type: String) : Filter.Text(name)

class SortFilter : Filter.Select<String>("Sort by", sortValues.map { it.first }.toTypedArray()) {
    fun getValue() = sortValues[state].second
}

private val sortValues = listOf(
    Pair("Recently Posted", "4"),
    Pair("Title", "2"),
    Pair("Pages", "3"),
    Pair("Most Viewed", "8"),
    Pair("Most Favorited", "9"),
)

class CategoryFilter :
    Filter.Group<CheckBoxFilter>(
        "Category",
        listOf(
            Pair("Manga", 2),
            Pair("Doujinshi", 4),
            Pair("Illustration", 8),
        ).map { CheckBoxFilter(it.first, it.second, true) },
    )

class CheckBoxFilter(name: String, val value: Int, state: Boolean) : Filter.CheckBox(name, state)

open class TagConditionFilter(
    title: String,
    options: List<Pair<String, String>>,
    val param: String,
) : UriPartFilter(
    title,
    options.toTypedArray(),
)

class TagIncludeCondition :
    TagConditionFilter(
        "Include condition",
        listOf(
            "AND" to "",
            "OR" to "1",
        ),
        "i",
    )
class TagExcludeCondition :
    TagConditionFilter(
        "Exclude condition",
        listOf(
            "OR" to "",
            "AND" to "1",
        ),
        "e",
    )

class TagFilter(
    title: String,
    tags: List<FilterDto>,
    excludedTags: Set<String> = emptySet(),
) : Filter.Group<TagTriState>(
    title,
    tags.map { tag ->
        val state = if (tag.name.trim().lowercase() in excludedTags) Filter.TriState.STATE_EXCLUDE else Filter.TriState.STATE_IGNORE
        TagTriState(tag.name, tag.id, state)
    },
)

class TagTriState(name: String, val id: Int, state: Int = STATE_IGNORE) : Filter.TriState(name, state)

open class UriPartFilter(
    displayName: String,
    private val vals: Array<Pair<String, String>>,
    state: Int = 0,
) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), state) {
    fun toUriPart() = vals[state].second
}
