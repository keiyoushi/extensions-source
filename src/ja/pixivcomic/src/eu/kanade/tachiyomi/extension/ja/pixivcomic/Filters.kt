package eu.kanade.tachiyomi.extension.ja.pixivcomic

import eu.kanade.tachiyomi.source.model.Filter

class TagsFilter : Filter.Text("Tag Search")

class CategoryFilter(categories: List<String>) : Filter.Select<String>("Category", categories.toTypedArray()) {
    val selected get() = values[state]
}
