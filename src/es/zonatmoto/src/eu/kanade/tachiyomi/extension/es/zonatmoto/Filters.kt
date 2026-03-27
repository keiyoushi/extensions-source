package eu.kanade.tachiyomi.extension.es.zonatmoto

import eu.kanade.tachiyomi.source.model.Filter

val GENRES = arrayOf(
    "Acción" to "2",
    "Animación" to "87",
    "Apocalíptico" to "74",
    "Artes Marciales" to "23",
    "Aventura" to "3",
    "Boys Love" to "46",
    "Ciberpunk" to "66",
    "Ciencia Ficción" to "16",
    "Comedia" to "4",
    "Crimen" to "34",
    "Demonios" to "44",
    "Deporte" to "32",
    "Drama" to "9",
    "Ecchi" to "25",
    "Extranjero" to "81",
    "Familia" to "75",
    "Fantasia" to "5",
    "Girls Love" to "21",
    "Gore" to "61",
    "Guerra" to "76",
    "Género Bender" to "62",
    "Harem" to "8",
    "Historia" to "42",
    "Horror" to "43",
    "Magia" to "6",
    "Mecha" to "55",
    "Militar" to "64",
    "Misterio" to "33",
    "Musica" to "71",
    "Niños" to "63",
    "Oeste" to "54",
    "Parodia" to "73",
    "Policiaco" to "51",
    "Psicológico" to "31",
    "Realidad" to "56",
    "Realidad Virtual" to "24",
    "Recuentos de la vida" to "26",
    "Reencarnación" to "41",
    "Romance" to "15",
    "Samurái" to "45",
    "Sobrenatural" to "7",
    "Superpoderes" to "53",
    "Supervivencia" to "52",
    "Telenovela" to "72",
    "Thriller" to "36",
    "Tragedia" to "35",
    "Traps" to "82",
    "Vampiros" to "65",
    "Vida Escolar" to "22",
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
