package eu.kanade.tachiyomi.extension.es.orckumangas

import eu.kanade.tachiyomi.source.model.Filter

class StatusFilter :
    Filter.Select<String>(
        "Estado",
        statuses.map { it.first }.toTypedArray(),
    ) {
    val selected get() = statuses.getOrNull(state)?.second ?: ""

    companion object {
        private val statuses = listOf(
            "Todos" to "",
            "En curso" to "ongoing",
            "Finalizado" to "completed",
            "Hiatus" to "hiatus",
            "Cancelado" to "cancelled",
        )
    }
}

class TypeFilter :
    Filter.Select<String>(
        "Tipo",
        types.map { it.first }.toTypedArray(),
    ) {
    val selected get() = types.getOrNull(state)?.second ?: ""

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
