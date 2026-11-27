package eu.kanade.tachiyomi.extension.pt.blackoutcomics

import eu.kanade.tachiyomi.source.model.Filter

class StatusFilter :
    Filter.Select<String>(
        "Status",
        arrayOf("Todos", "Completos"),
    ) {
    val selected: String? get() = if (state == 1) "completed" else null
}

class GenreFilter(private val genres: List<String>) :
    Filter.Select<String>(
        "Gênero",
        arrayOf("Todos") + genres,
    ) {
    val selected: String? get() = if (state == 0) null else genres[state - 1]
}
