package eu.kanade.tachiyomi.extension.es.mangalatino

import eu.kanade.tachiyomi.source.model.Filter

class GenreFilter() : UriPartFilter(
    "Tags",
    arrayOf(
        Pair("Acción", "accion"),
        Pair("Animación", "animacion"),
        Pair("Apocalíptico", "apocaliptico"),
        Pair("Artes Marciales", "artes-marciales"),
        Pair("Aventura", "aventura"),
        Pair("Boys Love", "boys-love"),
        Pair("Ciberpunk", "ciberpunk"),
        Pair("Ciencia Ficción", "ciencia-ficcion"),
        Pair("Comedia", "comedia"),
        Pair("Crimen", "crimen"),
        Pair("Demonios", "demonios"),
        Pair("Deporte", "deporte"),
        Pair("Drama", "drama"),
        Pair("Ecchi", "ecchi"),
        Pair("Extranjero", "extranjero"),
        Pair("Familia", "familia"),
        Pair("Fantasia", "fantasia"),
        Pair("Género Bender", "genero-bender"),
        Pair("Girls Love", "girls-love"),
        Pair("Gore", "gore"),
        Pair("Guerra", "guerra"),
        Pair("Harem", "harem"),
        Pair("Historia", "historia"),
        Pair("Horror", "horror"),
        Pair("Magia", "magia"),
        Pair("Mecha", "mecha"),
        Pair("Militar", "militar"),
        Pair("Misterio", "misterio"),
        Pair("Musica", "musica"),
        Pair("Niños", "ninos"),
        Pair("Oeste", "oeste"),
        Pair("Parodia", "parodia"),
        Pair("Policiaco", "policiaco"),
        Pair("Psicológico", "psicologico"),
        Pair("Realidad", "realidad"),
        Pair("Realidad Virtual", "realidad-virtual"),
        Pair("Recuentos de la vida", "recuentos-de-la-vida"),
        Pair("Reencarnación", "reencarnacion"),
        Pair("Romance", "romance"),
        Pair("Samurái", "samurai"),
        Pair("Sobrenatural", "sobrenatural"),
        Pair("Superpoderes", "superpoderes"),
        Pair("Supervivencia", "supervivencia"),
        Pair("Telenovela", "telenovela"),
        Pair("Thriller", "thriller"),
        Pair("Tragedia", "tragedia"),
        Pair("Vampiros", "vampiros"),
        Pair("Vida Escolar", "vida-escolar"),
    ),
)

open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
    Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}
