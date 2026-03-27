package eu.kanade.tachiyomi.extension.es.zonatmoto

import eu.kanade.tachiyomi.source.model.Filter

val GENRES = arrayOf(
    "Acción" to "2",
    "Animación" to "6198",
    "Apocalíptico" to "861",
    "Artes Marciales" to "26",
    "Aventura" to "3",
    "Boys Love" to "103",
    "Ciberpunk" to "356",
    "Ciencia Ficción" to "21",
    "Comedia" to "4",
    "Crimen" to "41",
    "Demonios" to "88",
    "Deporte" to "37",
    "Drama" to "15",
    "Ecchi" to "32",
    "Extranjero" to "1168",
    "Familia" to "1027",
    "Fantasia" to "5",
    "Girls Love" to "22",
    "Gore" to "181",
    "Guerra" to "1109",
    "Género Bender" to "183",
    "Harem" to "8",
    "Historia" to "81",
    "Horror" to "82",
    "Magia" to "6",
    "Mecha" to "144",
    "Militar" to "342",
    "Misterio" to "40",
    "Musica" to "403",
    "Niños" to "219",
    "Oeste" to "141",
    "Parodia" to "820",
    "Policiaco" to "111",
    "Psicológico" to "36",
    "Realidad" to "147",
    "Realidad Virtual" to "27",
    "Recuentos de la vida" to "33",
    "Reencarnación" to "60",
    "Romance" to "16",
    "Samurái" to "99",
    "Sobrenatural" to "7",
    "Superpoderes" to "116",
    "Supervivencia" to "112",
    "Telenovela" to "470",
    "Thriller" to "49",
    "Tragedia" to "46",
    "Traps" to "1464",
    "Vampiros" to "345",
    "Vida Escolar" to "23",
)

class GenreFilter :
    Filter.Group<CheckBoxFilter>(
        "Géneros",
        GENRES.map { CheckBoxFilter(it.first, it.second) },
    )

val STATUSES = arrayOf(
    "Publicándose" to "12",
    "Finalizado" to "19",
    "Pausado" to "174",
    "Cancelado" to "198",
)

class StatusFilter :
    Filter.Group<CheckBoxFilter>(
        "Estado",
        STATUSES.map { CheckBoxFilter(it.first, it.second) },
    )

val TYPES = arrayOf(
    "Doujinshi" to "207",
    "Manga" to "14",
    "Manhua" to "31",
    "Manhwa" to "87",
    "Novela" to "214",
    "OEL" to "976",
    "One shot" to "12312",
)

class TypeFilter :
    Filter.Group<CheckBoxFilter>(
        "Tipo",
        TYPES.map { CheckBoxFilter(it.first, it.second) },
    )

class CheckBoxFilter(name: String, val value: String) : Filter.CheckBox(name)
