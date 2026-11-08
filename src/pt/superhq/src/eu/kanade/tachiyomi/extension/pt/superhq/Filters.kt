package eu.kanade.tachiyomi.extension.pt.superhq

import eu.kanade.tachiyomi.source.model.Filter
import kotlinx.serialization.Serializable

@Serializable
class Category(val name: String, val slug: String)

class CategoryFilter(private val categories: List<Category>) : Filter.Select<String>("Categoria", (listOf("Todas") + categories.map { it.name }).toTypedArray()) {
    val selected get() = categories.getOrNull(state - 1)?.slug
}
