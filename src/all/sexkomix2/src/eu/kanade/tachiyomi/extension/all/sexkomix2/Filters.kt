package eu.kanade.tachiyomi.extension.all.sexkomix2

import eu.kanade.tachiyomi.source.model.Filter

const val SORT_VIEWS = "prosmotr"
const val SORT_DATE = "date"

class SortFilter : Filter.Select<String>("Sort by", SORTS.map { it.first }.toTypedArray()) {
    val value get() = SORTS[state].second

    companion object {
        private val SORTS = listOf(
            "Views" to SORT_VIEWS,
            "Likes" to "like",
            "Date" to SORT_DATE,
        )
    }
}

class CategoryFilter(private val categories: List<String>) : Filter.Select<String>("Category", (listOf("Any") + categories).toTypedArray()) {
    val selected get() = categories.getOrNull(state - 1)
}

class TagFilter : Filter.Text("Tag")

class StudioFilter : Filter.Text("Studio")

class ListFilter : Filter.Select<String>("List", arrayOf("All", "Best")) {
    val best get() = state == 1
}
