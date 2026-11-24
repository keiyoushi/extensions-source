package eu.kanade.tachiyomi.extension.id.areakomik

import eu.kanade.tachiyomi.source.model.Filter
import kotlinx.serialization.Serializable

@Serializable
class GenreDto(
    val name: String,
    val slug: String,
)

class StatusFilter :
    Filter.Select<String>(
        "Status",
        arrayOf("Semua", "Ongoing", "Completed", "Hiatus"),
    ) {
    val selected: String? get() = if (state == 0) null else values[state].lowercase()
}

class GenreFilter(private val genres: List<GenreDto>) :
    Filter.Select<String>(
        "Genre",
        arrayOf("Semua") + genres.map { it.name },
    ) {
    val selected: String? get() = if (state == 0) null else genres[state - 1].slug
}
