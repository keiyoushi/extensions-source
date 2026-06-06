package eu.kanade.tachiyomi.extension.es.catmanhwas

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

abstract class SelectFilter(displayName: String, private val vals: List<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    val selected get() = vals[state].second
}

class GenreFilter(genres: List<Pair<String, String>>) : SelectFilter("Género", genres)

class OrderFilter(orders: List<Pair<String, String>>) : SelectFilter("Ordenar por", orders)

fun getFilters(): FilterList = FilterList(
    GenreFilter(
        listOf(
            Pair("Todos", ""),
            Pair("+19", "1"),
            Pair("Acción", "2"),
            Pair("Adulto", "3"),
            Pair("Apocalíptico", "4"),
            Pair("Aventura", "5"),
            Pair("BDSM", "6"),
            Pair("BL", "7"),
            Pair("Ciencia Ficción", "8"),
            Pair("Comedia", "9"),
            Pair("Crimen", "10"),
            Pair("Demonios", "11"),
            Pair("Deportes", "12"),
            Pair("Descensurado", "13"),
            Pair("Drama", "14"),
            Pair("Ecchi", "15"),
            Pair("Familia", "16"),
            Pair("Fantasía", "17"),
            Pair("Gender Bender", "18"),
            Pair("GL", "19"),
            Pair("Gogogo", "20"),
            Pair("Harem", "21"),
            Pair("Histórico", "22"),
            Pair("Horror", "23"),
            Pair("Isekai", "24"),
            Pair("Josei", "25"),
            Pair("Magia", "26"),
            Pair("Mazmorras", "27"),
            Pair("Militar", "28"),
            Pair("Misterio", "29"),
            Pair("Omegaverse", "30"),
            Pair("Psicológico", "31"),
            Pair("Reencarnación", "32"),
            Pair("Regresión", "33"),
            Pair("Romance", "34"),
            Pair("Seinen", "35"),
            Pair("Shoujo", "36"),
            Pair("Shounen", "37"),
            Pair("Sistemas", "38"),
            Pair("Smut", "39"),
            Pair("Sobrenatural", "40"),
            Pair("Soft BL", "41"),
            Pair("Supervivencia", "42"),
            Pair("Terror Psicológico", "43"),
            Pair("Thriller", "44"),
            Pair("Tragedia", "45"),
            Pair("Trasmigración", "46"),
            Pair("Vampiros", "47"),
            Pair("Venganza", "48"),
            Pair("Vida cotidiana", "49"),
            Pair("Vida escolar", "50"),
            Pair("Videojuegos", "51"),
            Pair("Wuxia", "52"),
        ),
    ),
    OrderFilter(
        listOf(
            Pair("Alfabético A-Z", "name"),
            Pair("Más recientes", "recent"),
            Pair("Más populares", "popular"),
            Pair("Mejor calificados", "rating"),
        ),
    ),
)
