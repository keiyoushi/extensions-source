package eu.kanade.tachiyomi.extension.pt.superhentai

import eu.kanade.tachiyomi.source.model.Filter

open class PathFilter(name: String, items: Map<String, String>) : Filter.Select<String>(name, arrayOf("Todas") + items.keys) {
    private val paths = listOf(null) + items.values

    fun selectedPath(): String? = paths[state]
}

class CategoryFilter(items: Map<String, String>) : PathFilter("Categoria", items)

class ParodyFilter(items: Map<String, String>) : PathFilter("Paródia", items)
