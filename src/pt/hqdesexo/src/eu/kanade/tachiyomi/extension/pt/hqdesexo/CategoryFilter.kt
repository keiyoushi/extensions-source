package eu.kanade.tachiyomi.extension.pt.hqdesexo

import eu.kanade.tachiyomi.source.model.Filter

class CategoryFilter(private val categories: List<Pair<String, String>>) :
    Filter.Select<String>(
        "Categoria",
        (listOf("Todas") + categories.map { it.first }).toTypedArray(),
    ) {
    val selected get() = categories.getOrNull(state - 1)?.second
}
