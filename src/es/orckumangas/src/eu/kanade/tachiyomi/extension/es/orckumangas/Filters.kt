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

class GenreFilter :
    Filter.Select<String>(
        "Género",
        genres.map { it.first }.toTypedArray(),
    ) {
    val selected get() = genres[state].second

    companion object {
        private val genres = listOf(
            Pair("Todos", "0"),
            Pair("Acción", "1"),
            Pair("Adulto", "22"),
            Pair("Ahegao", "23"),
            Pair("Artes Marciales", "7"),
            Pair("Aventura", "2"),
            Pair("BDSM", "30"),
            Pair("Chantaje", "16"),
            Pair("Comedia", "4"),
            Pair("Drama", "12"),
            Pair("Ecchi", "26"),
            Pair("Escolar", "18"),
            Pair("Exhibición", "24"),
            Pair("Fantasía", "3"),
            Pair("Gore", "33"),
            Pair("Harem", "10"),
            Pair("Isekai", "14"),
            Pair("Josei", "15"),
            Pair("MILFS", "21"),
            Pair("Misterio", "6"),
            Pair("NTR", "17"),
            Pair("RAPE", "29"),
            Pair("Recuentos de la Vida", "20"),
            Pair("Romance", "5"),
            Pair("Seinen", "9"),
            Pair("Shota", "31"),
            Pair("Shoujo", "19"),
            Pair("Sobrenatural", "28"),
            Pair("Tragedia", "32"),
            Pair("Ventanas", "13"),
            Pair("Webtoon", "27"),
            Pair("Yuri", "34"),
        )
    }
}
