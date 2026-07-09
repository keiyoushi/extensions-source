package eu.kanade.tachiyomi.extension.es.neomanga

import eu.kanade.tachiyomi.source.model.Filter

class StatusFilter : Filter.Select<String>("Estado", arrayOf("Todos", "En emisión", "Finalizado", "Pausado")) {
    val selectedValue: String
        get() = when (state) {
            1 -> "en_emision"
            2 -> "finalizado"
            3 -> "pausado"
            else -> "all"
        }
}

class GenreFilter :
    Filter.Select<String>(
        "Género",
        arrayOf(
            "Todos",
            "Acción",
            "Aventura",
            "Comedia",
            "Drama",
            "Fantasía",
            "Romance",
            "Ciencia Ficción",
            "Sobrenatural",
            "Artes Marciales",
            "Histórico",
            "Horror",
            "Misterio",
            "Psicológico",
            "Slice of Life",
            "Deportes",
            "Isekai",
            "Murim",
            "Reencarnación",
            "Cultivación",
        ),
    ) {
    val selectedValue: String
        get() = values[state]
}
