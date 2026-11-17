package eu.kanade.tachiyomi.extension.all.pornsticky

import eu.kanade.tachiyomi.source.model.Filter
import kotlinx.serialization.Serializable

@Serializable
class CategoryDto(val name: String, val slug: String)

class CategoryFilter(private val categories: List<CategoryDto>) : Filter.Select<String>("Category", arrayOf("Any") + categories.map { it.name }) {
    val selected get() = categories.getOrNull(state - 1)?.slug
}
