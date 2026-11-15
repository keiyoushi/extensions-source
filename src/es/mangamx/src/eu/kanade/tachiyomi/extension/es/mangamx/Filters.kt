package eu.kanade.tachiyomi.extension.es.mangamx

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

class StatusFilter :
    UriPartFilter(
        "Estado",
        arrayOf(
            Pair("Estado", "false"),
            Pair("En desarrollo", "1"),
            Pair("Completo", "0"),
        ),
    )

class TypeFilter :
    UriPartFilter(
        "Tipo",
        arrayOf(
            Pair("Todo", "false"),
            Pair("Mangas", "0"),
            Pair("Manhwas", "1"),
            Pair("One Shot", "2"),
            Pair("Manhuas", "3"),
            Pair("Novelas", "4"),
        ),
    )

class GenreFilter :
    UriPartFilter(
        "Géneros",
        arrayOf(
            Pair("Todos", "false"),
            Pair("Comedia", "1"),
            Pair("Drama", "2"),
            Pair("Acción", "3"),
            Pair("Escolar", "4"),
            Pair("Romance", "5"),
            Pair("Ecchi", "6"),
            Pair("Aventura", "7"),
            Pair("Shōnen", "8"),
            Pair("Shōjo", "9"),
            Pair("Deportes", "10"),
            Pair("Psicológico", "11"),
            Pair("Fantasía", "12"),
            Pair("Mecha", "13"),
            Pair("Gore", "14"),
            Pair("Yaoi", "15"),
            Pair("Yuri", "16"),
            Pair("Misterio", "17"),
            Pair("Sobrenatural", "18"),
            Pair("Seinen", "19"),
            Pair("Ficción", "20"),
            Pair("Harem", "21"),
            Pair("Webtoon", "25"),
            Pair("Histórico", "27"),
            Pair("Músical", "30"),
            Pair("Ciencia ficción", "31"),
            Pair("Shōjo-ai", "32"),
            Pair("Josei", "33"),
            Pair("Magia", "34"),
            Pair("Artes Marciales", "35"),
            Pair("Horror", "36"),
            Pair("Demonios", "37"),
            Pair("Supervivencia", "38"),
            Pair("Recuentos de la vida", "39"),
            Pair("Shōnen ai", "40"),
            Pair("Militar", "41"),
            Pair("Eroge", "42"),
            Pair("Isekai", "43"),
        ),
    )

class AdultContentFilter :
    UriPartFilter(
        "Contenido +18",
        arrayOf(
            Pair("Mostrar todo", "false"),
            Pair("Mostrar solo +18", "1"),
            Pair("No mostrar +18", "0"),
        ),
    )

class SortBy :
    Filter.Sort(
        "Ordenar por",
        arrayOf("Visitas", "Recientes", "Alfabético"),
        Selection(0, false),
    ) {
    fun toUriPart() = when (state?.index) {
        0 -> "visitas"
        1 -> "id"
        2 -> "nombre"
        else -> "visitas"
    }
}
