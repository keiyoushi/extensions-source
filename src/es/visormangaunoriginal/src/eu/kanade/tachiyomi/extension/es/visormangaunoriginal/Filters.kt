package eu.kanade.tachiyomi.extension.es.visormangaunoriginal

import eu.kanade.tachiyomi.source.model.Filter
import kotlinx.serialization.Serializable

@Serializable
class Genre(val name: String, val slug: String)

class GenreFilter(private val genres: List<Genre>) : Filter.Select<String>("Género (se ignora al buscar por texto)", arrayOf("Todos") + genres.map { it.name }) {
    val selected get() = genres.getOrNull(state - 1)?.slug
}
