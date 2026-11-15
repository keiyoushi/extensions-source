package eu.kanade.tachiyomi.extension.all.historyofhyrule

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

class CategoryFilter : Filter.Select<String>("Category", (listOf("All") + CATEGORIES.map { it.first }).toTypedArray()) {
    val category get() = CATEGORIES.getOrNull(state - 1)?.second
}

fun getFilters() = FilterList(CategoryFilter())

// The categories of the site's publications, by the name of their list page
val CATEGORIES = listOf(
    "Manga" to "manga",
    "Doujinshi" to "doujinshi",
    "Comics" to "comics",
    "4koma" to "4koma",
    "Novels & Books" to "book",
    "Zines" to "zines",
    "Guides" to "guide",
    "Misc" to "misc",
)
