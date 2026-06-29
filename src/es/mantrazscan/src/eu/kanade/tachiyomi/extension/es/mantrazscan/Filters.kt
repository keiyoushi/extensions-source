package eu.kanade.tachiyomi.extension.es.mantrazscan

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

class GenreFilter :
    UriPartFilter(
        "Género",
        arrayOf(
            Pair("Todos", ""),
            Pair("Acción", "accion"),
            Pair("Romance", "romance"),
            Pair("Fantasía", "fantasia"),
            Pair("Drama", "drama"),
            Pair("Comedia", "comedia"),
            Pair("Thriller", "thriller"),
            Pair("Artes Marciales", "artes-marciales"),
            Pair("Harem", "harem"),
            Pair("Isekai", "isekai"),
            Pair("Horror", "horror"),
            Pair("Vida Escolar", "vida-escolar"),
            Pair("Seinen", "seinen"),
            Pair("Shounen", "shounen"),
        ),
    )

class StatusFilter :
    UriPartFilter(
        "Estado",
        arrayOf(
            Pair("Todos", ""),
            Pair("En curso", "ongoing"),
            Pair("Completado", "completed"),
            Pair("Hiatus", "hiatus"),
        ),
    )

class SortFilter :
    UriPartFilter(
        "Ordenar",
        arrayOf(
            Pair("Recientes", "updated"),
            Pair("Vistas", "views"),
            Pair("Rating", "rating"),
        ),
    )

class TypeFilter :
    UriPartFilter(
        "Tipo",
        arrayOf(
            Pair("Todos", ""),
            Pair("Adulto", "adult"),
            Pair("BL", "bl"),
            Pair("Shoujo", "shoujo"),
            Pair("Manga", "manga"),
        ),
    )
