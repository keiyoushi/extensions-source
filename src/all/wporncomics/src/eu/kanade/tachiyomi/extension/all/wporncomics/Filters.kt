package eu.kanade.tachiyomi.extension.all.wporncomics

import eu.kanade.tachiyomi.source.model.Filter

const val SORT_VIEWS = 0
const val SORT_LIKES = 1
const val SORT_DATE = 2

class SortFilter : Filter.Select<String>("Sort by", arrayOf("Views", "Likes", "Date"))

class CategoryFilter(private val categories: List<String>) : Filter.Select<String>("Category", (listOf("Any") + categories).toTypedArray()) {
    val selected get() = categories.getOrNull(state - 1)
}

class TagFilter : Filter.Text("Tag")

class StudioFilter : Filter.Text("Studio")

class ListFilter : Filter.Select<String>("List", arrayOf("All", "Best")) {
    val best get() = state == 1
}
