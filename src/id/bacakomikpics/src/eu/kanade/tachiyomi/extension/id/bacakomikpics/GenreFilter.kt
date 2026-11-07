package eu.kanade.tachiyomi.extension.id.bacakomikpics

import eu.kanade.tachiyomi.source.model.Filter

class GenreFilter(private val genres: List<Pair<String, String>>) : Filter.Select<String>("Genre", (listOf("All") + genres.map { it.first }).toTypedArray()) {
    val genreId get() = genres.getOrNull(state - 1)?.second
}
