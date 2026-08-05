package eu.kanade.tachiyomi.extension.en.doujinio

import eu.kanade.tachiyomi.source.model.Filter

val sortOptions = listOf(
    "Date" to "published_at",
    "Alphabetical" to "hidden_title",
)

class SortFilter(
    name: String = "Sort by",
    selection: Filter.Sort.Selection = Filter.Sort.Selection(0, false),
) : Filter.Sort(
    name,
    sortOptions.map { it.first }.toTypedArray(),
    selection,
) {
    val sort: String
        get() = sortOptions[state!!.index].second
    val order: String
        get() = if (state!!.ascending) "asc" else "desc"
}

class TagFilter(name: String, val id: Int) : Filter.CheckBox(name, false)

class TagGroup(tags: List<Tag>) :
    Filter.Group<TagFilter>(
        "Tags",
        tags.map { TagFilter(it.name, it.id) },
    ) {
    val tagIds: List<Int>
        get() = state.filter { it.state }.map { it.id }
}
