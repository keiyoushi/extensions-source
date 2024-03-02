package eu.kanade.tachiyomi.extension.es.manhuako

import eu.kanade.tachiyomi.source.model.Filter

class TypeFilter : UriPartFilter(
    "Tipo",
    arrayOf(
        Pair("Todos", "home"),
        Pair("Mangas", "manga"),
        Pair("Manhuas", "manhua"),
        Pair("Manhwas", "manhwa"),
    ),
)

class GenreFilter : UriPartFilter(
    "Géneros",
    arrayOf(
        Pair("Todos", ""),
        Pair("Accion", "accion"),
        Pair("Harem", "harem"),
        Pair("Cultivo", "cultivo"),
        Pair("Romance", "romance"),
        Pair("Aventura", "aventura"),
        Pair("Isekai", "isekai"),
        Pair("Escolar", "escolar"),
        Pair("Artes Marciales", "artes-marciales"),
        Pair("Sistema", "sistema"),
        Pair("Fantasia", "fantasia"),
        Pair("Apocaliptico", "apocaliptico"),
        Pair("Sobrenatural", "sobrenatural"),
        Pair("Supervivencia", "supervivencia"),
        Pair("Manhua", "manhua"),
        Pair("Manga", "manga"),
        Pair("Manhwa", "manhwa"),
        Pair("Ecchi", "ecchi"),
        Pair("Gore", "gore"),
        Pair("Terror", "terror"),
        Pair("Suspenso", "suspenso"),
        Pair("Magia", "magia"),
        Pair("Psicologico", "psicologico"),
        Pair("Recuentos de la vida", "recuentos-de-la-vida"),
        Pair("Drama", "drama"),
        Pair("Comedia", "comedia"),
        Pair("Shonen", "shonen"),
        Pair("Josei", "josei"),
        Pair("Seinen", "seinen"),
    ),
)

open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
    Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}
