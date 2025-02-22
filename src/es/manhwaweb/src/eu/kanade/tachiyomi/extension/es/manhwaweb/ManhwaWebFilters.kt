package eu.kanade.tachiyomi.extension.es.manhwaweb

import eu.kanade.tachiyomi.source.model.Filter

class TypeFilter : UriPartFilter(
    "Tipo",
    arrayOf(
        Pair("Ver todo", ""),
        Pair("Manhwa", "manhwa"),
        Pair("Manga", "manga"),
        Pair("Manhua", "manhua"),
    ),
)

class DemographyFilter : UriPartFilter(
    "Demografía",
    arrayOf(
        Pair("Ver todo", ""),
        Pair("Seinen", "seinen"),
        Pair("Shonen", "shonen"),
        Pair("Josei", "josei"),
        Pair("Shojo", "shojo"),
    ),
)

class StatusFilter : UriPartFilter(
    "Estado",
    arrayOf(
        Pair("Ver todo", ""),
        Pair("Publicándose", "publicandose"),
        Pair("Finalizado", "finalizado"),
    ),
)

class EroticFilter : UriPartFilter(
    "Erótico",
    arrayOf(
        Pair("Ver todo", ""),
        Pair("Sí", "si"),
        Pair("No", "no"),
    ),
)

class Genre(title: String, val id: Int) : Filter.CheckBox(title)

class GenreFilter(title: String, genres: List<Genre>) : Filter.Group<Genre>(title, genres)

fun getGenres(): List<Genre> = listOf(
    Genre("Acción", 3),
    Genre("Aventura", 29),
    Genre("Comedia", 18),
    Genre("Drama", 1),
    Genre("Recuentos de la vida", 42),
    Genre("Romance", 2),
    Genre("Venganza", 5),
    Genre("Harem", 6),
    Genre("Fantasia", 23),
    Genre("Sobrenatural", 31),
    Genre("Tragedia", 25),
    Genre("Psicológico", 43),
    Genre("Horror", 32),
    Genre("Thriller", 44),
    Genre("Historias cortas", 28),
    Genre("Ecchi", 30),
    Genre("Gore", 34),
    Genre("Girls love", 27),
    Genre("Boys love", 45),
    Genre("Reencarnación", 41),
    Genre("Sistema de niveles", 37),
    Genre("Ciencia ficción", 33),
    Genre("Apocalíptico", 38),
    Genre("Artes Marciales", 39),
    Genre("Superpoderes", 40),
    Genre("Cultivación (cultivo)", 35),
    Genre("Milf", 8),
)

class SortProperty(val name: String, val value: String) {
    override fun toString(): String = name
}

fun getSortProperties(): List<SortProperty> = listOf(
    SortProperty("Alfabético", "alfabetico"),
    SortProperty("Creación", "creacion"),
    SortProperty("Num. Capítulos", "num_chapter"),
)

class SortByFilter(title: String, private val sortProperties: List<SortProperty>) : Filter.Sort(
    title,
    sortProperties.map { it.name }.toTypedArray(),
    Selection(0, ascending = false),
) {
    val selected: String
        get() = sortProperties[state!!.index].value
}

open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
    Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}
