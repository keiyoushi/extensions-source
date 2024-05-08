package eu.kanade.tachiyomi.extension.es.akaya

import eu.kanade.tachiyomi.source.model.Filter

class Genre(name: String, val id: Int) : Filter.CheckBox(name)
open class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Géneros", genres)

class GenreFilter : GenreList(
    listOf(
        Genre("Acción", 9),
        Genre("Arte", 34),
        Genre("Boylove (yaoi)", 18),
        Genre("Comedia", 21),
        Genre("Crimen", 25),
        Genre("Distópico", 15),
        Genre("Drama", 35),
        Genre("Fantasía", 8),
        Genre("Girllove (yuri)", 27),
        Genre("Isekai", 19),
        Genre("LGBT", 16),
        Genre("Monstruos", 10),
        Genre("NSFW", 17),
        Genre("Psicológico", 26),
        Genre("Romance", 24),
        Genre("Sci Fi", 23),
        Genre("Slice of life", 13),
        Genre("Steampunk", 20),
        Genre("Superhéroe", 11),
        Genre("Supernatural", 22),
        Genre("Suspenso", 14),
        Genre("Thriller", 12),

    ),
)

class OrderFilter() : UriPartFilter(
    "Ordenar por",
    arrayOf(
        Pair("Populares", "genres"),
        Pair("Recientes", "genres-bydate"),
        Pair("Nombre", "genres-byname"),
    ),
)

open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) :
    Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}
