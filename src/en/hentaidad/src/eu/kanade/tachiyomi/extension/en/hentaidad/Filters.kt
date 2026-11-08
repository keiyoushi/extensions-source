package eu.kanade.tachiyomi.extension.en.hentaidad

import eu.kanade.tachiyomi.source.model.Filter
import kotlinx.serialization.Serializable

@Serializable
class Tag(val name: String, val slug: String)

class TagFilter(private val tags: List<Tag>) : Filter.Select<String>("Series or tag", (listOf("Any") + tags.map { it.name }).toTypedArray()) {
    val selected get() = tags.getOrNull(state - 1)?.slug
}
