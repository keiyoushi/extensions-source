package eu.kanade.tachiyomi.extension.es.orckumangas

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter :
    Filter.Select<String>(
        "Ordenar por",
        sorts.map { it.first }.toTypedArray(),
        1,
    ) {
    val selected get() = sorts[state].second

    companion object {
        private val sorts = listOf(
            "Más recientes" to "recientes",
            "Más vistos" to "vistas",
            "Mejor puntuados" to "rating",
            "Más capítulos" to "capitulos",
            "A–Z" to "alfabetico",
        )
    }
}

class StatusFilter :
    Filter.Select<String>(
        "Estado",
        statuses.map { it.first }.toTypedArray(),
    ) {
    val selected get() = statuses[state].second

    companion object {
        private val statuses = listOf(
            "Cualquiera" to "",
            "En emisión" to "ongoing",
            "Finalizado" to "completed",
            "En pausa" to "hiatus",
            "Cancelado" to "cancelled",
        )
    }
}

class TypeFilter :
    Filter.Select<String>(
        "Tipo",
        types.map { it.first }.toTypedArray(),
    ) {
    val selected get() = types[state].second

    companion object {
        private val types = listOf(
            "Todos" to "",
            "Manga" to "manga",
            "Manhwa" to "manhwa",
            "Manhua" to "manhua",
        )
    }
}

class GenreFilter(private val genres: List<Pair<String, String>>) :
    Filter.Select<String>(
        "Género",
        genres.map { it.first }.toTypedArray(),
    ) {
    val selected get() = genres[state].second
}
