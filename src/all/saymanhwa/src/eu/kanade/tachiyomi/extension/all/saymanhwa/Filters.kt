package eu.kanade.tachiyomi.extension.all.saymanhwa

import eu.kanade.tachiyomi.source.model.Filter
import kotlinx.serialization.Serializable

@Serializable
class GenreOption(
    val name: String,
    val value: String?,
)

class GenreFilter(title: String, genres: List<GenreOption>) :
    Filter.Select<String>(
        title,
        arrayOf("All") + genres.map { it.name }.toTypedArray(),
    ) {
    val vals = arrayOf<String?>(null) + genres.map { it.value }.toTypedArray()
}
