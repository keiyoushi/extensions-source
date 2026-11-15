package eu.kanade.tachiyomi.extension.all.freeadultcomix

import eu.kanade.tachiyomi.source.model.Filter

open class SelectFilter(name: String, items: Map<String, String>) : Filter.Select<String>(name, arrayOf("Any") + items.keys) {
    private val slugs = listOf(null) + items.values

    fun selected(): String? = slugs[state]
}

class CategoryFilter(items: Map<String, String>) : SelectFilter("Category", items)

class TagFilter(items: Map<String, String>) : SelectFilter("Tag", items)
