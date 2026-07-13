package eu.kanade.tachiyomi.extension.es.zonatmoorg

import eu.kanade.tachiyomi.source.model.Filter

val GENRES = arrayOf(
    "Acción" to "1",
    "Aventura" to "2",
    "Comedia" to "4",
    "Fantasia" to "5",
    "Magia" to "6",
    "Sobrenatural" to "7",
    "Harem" to "8",
    "Drama" to "15",
    "Romance" to "16",
    "Ciencia Ficción" to "21",
    "Girls Love" to "22",
    "Vida Escolar" to "23",
    "Artes Marciales" to "41",
    "Tragedia" to "46",
    "Apocalíptico" to "47",
    "Thriller" to "49",
    "Reencarnación" to "60",
    "Historia" to "81",
    "Horror" to "82",
    "Demonios" to "88",
    "Samurái" to "99",
    "Boys Love" to "103",
    "Policiaco" to "111",
    "Supervivencia" to "112",
    "Superpoderes" to "116",
    "Oeste" to "141",
    "Mecha" to "144",
    "Realidad" to "147",
    "Gore" to "181",
    "Género Bender" to "183",
    "Niños" to "219",
    "Novela" to "214",
    "Vampiros" to "345",
    "Militar" to "342",
    "Ciberpunk" to "356",
    "Musica" to "403",
    "Telenovela" to "470",
    "Guerra" to "1109",
    "Extranjero" to "1168",
    "Familia" to "1027",
    "Realidad Virtual" to "27",
    "Recuentos de la vida" to "33",
    "Animación" to "6198",
    "Parodia" to "820",
    "Traps" to "1464",
    "Ecchi" to "32",
    "Crimen" to "41",
)

class GenreFilter :
    Filter.Group<TriStateFilter>(
        "Géneros",
        GENRES.map { TriStateFilter(it.first, it.second) },
    )

class TriStateFilter(name: String, val value: String) : Filter.TriState(name)

val STATUSES = arrayOf(
    "Cualquiera" to "",
    "Publicándose" to "ongoing",
    "Finalizado" to "completed",
    "Terminado" to "ended",
    "Pausado" to "hiatus",
    "Cancelado" to "cancelled",
)

class StatusFilter :
    Filter.Select<String>(
        "Estado",
        STATUSES.map { it.first }.toTypedArray(),
    )

val TYPES = arrayOf(
    "Cualquiera" to "",
    "Manga" to "manga",
    "Manhua" to "manhua",
    "Manhwa" to "manhwa",
    "Webtoon" to "webtoon",
    "Novela" to "novel",
    "Comic" to "comic",
    "One shot" to "one_shot",
    "Doujinshi" to "doujinshi",
    "OEL" to "oel",
)

class TypeFilter :
    Filter.Select<String>(
        "Tipo",
        TYPES.map { it.first }.toTypedArray(),
    )

val DEMOGRAPHIES = arrayOf(
    "Cualquiera" to "",
    "Seinen" to "seinen",
    "Shoujo" to "shoujo",
    "Shounen" to "shounen",
    "Josei" to "josei",
    "Kodomo" to "kodomo",
)

class DemographyFilter :
    Filter.Select<String>(
        "Demografía",
        DEMOGRAPHIES.map { it.first }.toTypedArray(),
    )

val HOME_TABS = arrayOf(
    "Todo (Biblioteca)" to "",
    "Populares (Portada)" to "populars",
    "Trending (Portada)" to "trending",
)

class HomeTabFilter :
    Filter.Select<String>(
        "Pestaña de portada (Solo página 1)",
        HOME_TABS.map { it.first }.toTypedArray(),
    )

val SORT_COLUMNS = arrayOf(
    "Popularidad" to "likes_count",
    "Alfabético" to "alphabetically",
    "Valoración" to "score",
    "Más recientes" to "creation",
    "Fecha estreno" to "release_date",
    "Capítulos" to "num_chapters",
)

class SortFilter :
    Filter.Sort(
        "Ordenar por",
        SORT_COLUMNS.map { it.first }.toTypedArray(),
        Selection(0, false),
    )
