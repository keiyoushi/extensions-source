package eu.kanade.tachiyomi.extension.es.olympusscanlation

import eu.kanade.tachiyomi.source.model.Filter

class StatusFilter(
    private val options: List<Pair<String, Int>>,
) : Filter.Select<String>(
    "Estado",
    arrayOf("Todos") + options.map { it.first },
) {
    val id get() = options.getOrNull(state - 1)?.second
}

class GenreFilter(
    private val options: List<Pair<String, Int>>,
) : Filter.Select<String>(
    "Género",
    arrayOf("Todos") + options.map { it.first },
) {
    val id get() = options.getOrNull(state - 1)?.second
}
