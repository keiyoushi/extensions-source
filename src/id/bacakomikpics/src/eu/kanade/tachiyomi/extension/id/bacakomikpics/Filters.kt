package eu.kanade.tachiyomi.extension.id.bacakomikpics

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter :
    Filter.Select<String>(
        "Urutkan",
        arrayOf("Relevansi", "Update terbaru", "Terbaru", "Judul"),
    ) {
    val orderBy get() = arrayOf("relevance", "modified", "date", "title")[state]

    val order get() = if (orderBy == "title") "asc" else "desc"
}

class GenreFilter(private val genres: List<Pair<String, String>>) : Filter.Select<String>("Genre", (listOf("Semua") + genres.map { it.first }).toTypedArray()) {
    val genreId get() = genres.getOrNull(state - 1)?.second
}
