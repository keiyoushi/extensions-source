package eu.kanade.tachiyomi.extension.es.dynasty

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter :
    Filter.Select<String>(
        "Ordenar por",
        arrayOf("Recientes", "Populares", "Valorados", "A - Z"),
    ) {

    fun toUriPart() = when (state) {
        0 -> "newest"
        1 -> "popular"
        2 -> "rating"
        3 -> "az"
        else -> "popular"
    }
}

class GenreFilter : Filter.Select<String>("Género (Ignorado si hay texto en la búsqueda)", genres.map { it.first }.toTypedArray()) {
    fun toUriPart() = genres[state].second
}

private val genres = arrayOf(
    Pair("Todos", ""),
    Pair("Acción", "accion"),
    Pair("Artes Marciales", "artes-marciales"),
    Pair("Aventura", "aventura"),
    Pair("BL", "bl"),
    Pair("Ciencia Ficción", "ciencia-ficcion"),
    Pair("Comedia", "comedia"),
    Pair("Deportes", "deportes"),
    Pair("Drama", "drama"),
    Pair("Ecchi", "ecchi"),
    Pair("Escolar", "escolar"),
    Pair("Fantasía", "fantasia"),
    Pair("Harem", "harem"),
    Pair("HFY", "humanity-fvck-yeah"),
    Pair("Horror", "horror"),
    Pair("Isekai", "isekai"),
    Pair("Kingdom building", "kingdom-building"),
    Pair("Mecha", "mecha"),
    Pair("Misterio", "misterio"),
    Pair("Murim", "murim"),
    Pair("Psicológico", "psicologico"),
    Pair("Reencarnación", "reencarnacion"),
    Pair("Romance", "romance"),
    Pair("Seinen", "seinen"),
    Pair("Shounen", "shounen"),
    Pair("Sistemas", "sistemas"),
    Pair("Slice of Life", "slice-of-life"),
    Pair("Sobrenatural", "sobrenatural"),
    Pair("Tragedia", "tragedia"),
    Pair("Xianxia", "xianxia"),
    Pair("Xuanhuan", "xuanhuan"),
    Pair("Yuri", "yuri"),
)
