package eu.kanade.tachiyomi.extension.es.leermangaesp

import eu.kanade.tachiyomi.source.model.Filter

class Genre(name: String, val value: String) : Filter.CheckBox(name)
open class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Géneros", genres)

class GenreFilter :
    GenreList(
        listOf(
            Genre("Acción", "Acción"),
            Genre("Animación", "Animación"),
            Genre("Apocalíptico", "Apocalíptico"),
            Genre("Artes marciales", "Artes marciales"),
            Genre("Automóviles", "Automóviles"),
            Genre("Aventura", "Aventura"),
            Genre("Boys Love", "Boys Love"),
            Genre("Ciberpunk", "Ciberpunk"),
            Genre("Ciencia Ficción", "Ciencia Ficción"),
            Genre("Comedia", "Comedia"),
            Genre("Crimen", "Crimen"),
            Genre("Demonios", "Demonios"),
            Genre("Deporte", "Deporte"),
            Genre("Deportes", "Deportes"),
            Genre("Doujinshi", "Doujinshi"),
            Genre("Drama", "Drama"),
            Genre("Ecchi", "Ecchi"),
            Genre("Espacio exterior", "Espacio exterior"),
            Genre("Extranjero", "Extranjero"),
            Genre("Familia", "Familia"),
            Genre("Fantasía", "Fantasía"),
            Genre("Género Bender", "Género Bender"),
            Genre("Girls Love", "Girls Love"),
            Genre("Gore", "Gore"),
            Genre("Guerra", "Guerra"),
            Genre("Harem", "Harem"),
            Genre("Historia", "Historia"),
            Genre("Histórico", "Histórico"),
            Genre("Horror", "Horror"),
            Genre("Isekai", "Isekai"),
            Genre("Josei", "Josei"),
            Genre("Juegos", "Juegos"),
            Genre("Locura", "Locura"),
            Genre("Magia", "Magia"),
            Genre("Mecha", "Mecha"),
            Genre("Militar", "Militar"),
            Genre("Misterio", "Misterio"),
            Genre("Música", "Música"),
            Genre("Niños", "Niños"),
            Genre("Oeste", "Oeste"),
            Genre("Parodia", "Parodia"),
            Genre("Policía", "Policía"),
            Genre("Policiaco", "Policiaco"),
            Genre("Psicológico", "Psicológico"),
            Genre("Realidad", "Realidad"),
            Genre("Realidad Virtual", "Realidad Virtual"),
            Genre("Recuentos de la vida", "Recuentos de la vida"),
            Genre("Reencarnación", "Reencarnación"),
            Genre("Romance", "Romance"),
            Genre("Samurai", "Samurai"),
            Genre("Seinen", "Seinen"),
            Genre("Shoujo", "Shoujo"),
            Genre("Shoujo Ai", "Shoujo Ai"),
            Genre("Shounen", "Shounen"),
            Genre("Sobrenatural", "Sobrenatural"),
            Genre("Superpoderes", "Superpoderes"),
            Genre("Supervivencia", "Supervivencia"),
            Genre("Suspenso", "Suspenso"),
            Genre("Telenovela", "Telenovela"),
            Genre("Terror", "Terror"),
            Genre("Thriller", "Thriller"),
            Genre("Tragedia", "Tragedia"),
            Genre("Traps", "Traps"),
            Genre("Vampiros", "Vampiros"),
            Genre("Vida escolar", "Vida escolar"),
        ),
    )

class TypeFilter :
    UriPartFilter(
        "Tipo",
        arrayOf(
            Pair("Todos", ""),
            Pair("Manga", "Manga"),
            Pair("Manhwa", "Manhwa"),
            Pair("Manhua", "Manhua"),
            Pair("Novela", "Novela"),
        ),
    )

open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}
