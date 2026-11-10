package eu.kanade.tachiyomi.extension.es.hmanga

import eu.kanade.tachiyomi.source.model.Filter
import kotlinx.serialization.Serializable

@Serializable
class Tag(val id: Int, val name: String)

class TagState(val id: Int, name: String) : Filter.TriState(name)

class TagFilter(tags: List<TagState>) : Filter.Group<TagState>("Etiquetas", tags) {
    val included get() = state.filter { it.isIncluded() }.map { it.id }
    val excluded get() = state.filter { it.isExcluded() }.map { it.id }
}
