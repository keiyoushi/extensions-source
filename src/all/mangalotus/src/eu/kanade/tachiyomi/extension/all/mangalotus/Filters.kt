package eu.kanade.tachiyomi.extension.all.mangalotus

import eu.kanade.tachiyomi.source.model.Filter
import kotlinx.serialization.Serializable

@Serializable
class Option(val name: String, val value: String)

class CategoryFilter(private val options: List<Option>) : Filter.Select<String>("Category", (listOf("All") + options.map { it.name }).toTypedArray()) {
    val selected get() = options.getOrNull(state - 1)?.value
}
