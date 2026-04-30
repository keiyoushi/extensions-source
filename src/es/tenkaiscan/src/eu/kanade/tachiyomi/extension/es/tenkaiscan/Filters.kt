package eu.kanade.tachiyomi.extension.es.tenkaiscan

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(
    displayName: String,
    private val vals: Array<Pair<String, String>>,
) : Filter.Select<String>(
    displayName,
    vals.map { it.first }.toTypedArray(),
) {
    fun toUriPart() = vals[state].second
}

class AlphabeticFilter :
    UriPartFilter(
        "Ordenar por",
        arrayOf(
            Pair("<Seleccionar>", ""),
            Pair("A", "a"),
            Pair("B", "b"),
            Pair("C", "c"),
            Pair("D", "d"),
            Pair("E", "e"),
            Pair("F", "f"),
            Pair("G", "g"),
            Pair("H", "h"),
            Pair("I", "i"),
            Pair("J", "j"),
            Pair("K", "k"),
            Pair("L", "l"),
            Pair("M", "m"),
            Pair("N", "n"),
            Pair("O", "o"),
            Pair("P", "p"),
            Pair("Q", "q"),
            Pair("R", "r"),
            Pair("S", "s"),
            Pair("T", "t"),
            Pair("U", "u"),
            Pair("V", "v"),
            Pair("W", "w"),
            Pair("X", "x"),
            Pair("Y", "y"),
            Pair("Z", "z"),
        ),
    )

class GenreFilter :
    UriPartFilter(
        "Género",
        arrayOf(
            Pair("<Seleccionar>", ""),
            Pair("Adaptación de Novela", "Adaptación de Novela"),
            Pair("Aventuras", "Aventuras"),
            Pair("Bondage", "Bondage"),
            Pair("Comedia", "Comedia"),
            Pair("Drama", "Drama"),
            Pair("Ecchi", "Ecchi"),
            Pair("Escolar", "Escolar"),
            Pair("Fantasía", "Fantasía"),
            Pair("Hardcore", "Hardcore"),
            Pair("Harem", "Harem"),
            Pair("Isekai", "Isekai"),
            Pair("MILF", "MILF"),
            Pair("Netorare", "Netorare"),
            Pair("Novela", "Novela"),
            Pair("Recuentos de la vida", "Recuentos de la vida"),
            Pair("Romance", "Romance"),
            Pair("Seinen", "Seinen"),
            Pair("Sistemas", "Sistemas"),
            Pair("Venganza", "Venganza"),
        ),
    )

class StatusFilter :
    UriPartFilter(
        "Status",
        arrayOf(
            Pair("Completed", "Completed"),
            Pair("En Libertad", "En Libertad"),
            Pair("Canceled", "Canceled"),
        ),
    )
