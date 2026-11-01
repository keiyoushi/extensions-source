package eu.kanade.tachiyomi.extension.en.arvenscansnet

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter : Filter.Select<String>("Sort", arrayOf("Latest updates", "New")) {
    val sort get() = if (state == 0) "updated" else null
}

class GenreFilter(private val genres: List<GenreDto>) : Filter.Select<String>("Genre", (listOf("All") + genres.map { it.name }).toTypedArray()) {
    val slug get() = genres.getOrNull(state - 1)?.slug
}
