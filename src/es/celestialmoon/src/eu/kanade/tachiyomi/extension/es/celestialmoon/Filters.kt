package eu.kanade.tachiyomi.extension.es.celestialmoon

import eu.kanade.tachiyomi.source.model.Filter

class GenreFilter(genres: List<String>) : Filter.Select<String>("Género", (listOf("Todos") + genres).toTypedArray()) {
    val selected get() = values[state].takeIf { state > 0 }
}

class StatusFilter : Filter.Select<String>("Estado", arrayOf("Todos", "En emisión", "Finalizada", "En pausa")) {
    val selected get() = values[state].takeIf { state > 0 }
}

class SortFilter(state: Int = 0) :
    Filter.Select<String>(
        "Ordenar por",
        arrayOf("Más leídas (semana)", "Actualizaciones", "Título A-Z", "Más leídas (hoy)", "Más leídas (total)"),
        state,
    )
