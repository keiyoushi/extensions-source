package eu.kanade.tachiyomi.extension.en.hdporncomics

import eu.kanade.tachiyomi.source.model.Filter

class TypeFilter : Filter.Select<String>("Type", arrayOf("Comics", "Manhwa"))

class TagFilter(tags: Map<String, String>) : Filter.Select<String>("Comics tag", arrayOf("Any") + tags.keys) {
    private val slugs = listOf(null) + tags.values

    fun selectedPath() = slugs[state]
}
