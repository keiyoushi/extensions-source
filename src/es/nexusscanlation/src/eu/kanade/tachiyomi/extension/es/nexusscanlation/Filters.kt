package eu.kanade.tachiyomi.extension.es.nexusscanlation

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter(
    default: String = "popular",
) : SelectFilter(
    "Ordenar por",
    listOf(
        "Popular" to "popular",
        "Nuevo" to "nuevo",
        "A–Z" to "az",
        "Rating" to "rating",
    ),
    default,
)

class StatusFilter :
    SelectFilter(
        "Estado",
        listOf(
            "Todos" to "",
            "En Emisión" to "en_emision",
            "Completado" to "finalizado",
            "En Pausa" to "pausado",
            "Cancelado" to "cancelado",
        ),
    )

class TypeFilter :
    SelectFilter(
        "Tipo",
        listOf(
            "Todos" to "",
            "Manhwa" to "manhwa",
            "Manga" to "manga",
            "Manhua" to "manhua",
            "Novela" to "novel",
            "Manfra" to "manfra",
            "Doujin" to "doujin",
        ),
    )

class GenreFilter(genres: List<Pair<String, String>>) :
    SelectFilter(
        "Género",
        genres,
    )

open class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
    default: String = options.first().second,
) : Filter.Select<String>(
    name,
    options.map { it.first }.toTypedArray(),
    options.indexOfFirst { it.second == default }.coerceAtLeast(0),
) {
    fun selectedValue(): String = options[state].second
}

val DEFAULT_GENRES = listOf(
    "Todos" to "",
    "Acción" to "accion",
    "Adulto" to "adulto",
    "Aventura" to "aventura",
    "Bestias" to "bestias",
    "Boys Love" to "boys-love",
    "Campus" to "campus",
    "Casada/o" to "casados",
    "Ciencia Ficción" to "ciencia-ficcion",
    "Comedia" to "comedia",
    "Crimen" to "crimen",
    "Demonios" to "demonios",
    "Deporte" to "deporte",
    "Deportes" to "deportes",
    "Dominación" to "dominacion",
    "Drama" to "drama",
    "Ecchi" to "ecchi",
    "Escolar" to "escolar",
    "Familia" to "familia",
    "Fantasía" to "fantasia",
    "Fetiches" to "fetiches",
    "Género Bender" to "genero-bender",
    "Gore" to "gore",
    "Guerra" to "guerra",
    "Harem" to "harem",
    "Historia" to "historia",
    "Histórico" to "historico",
    "Humillación" to "humillacion",
    "Infidelidad" to "infidelidad",
    "Intercambio" to "intercambio",
    "Isekai" to "isekai",
    "Josei" to "josei",
    "Magia" to "magia",
    "Masoquismo" to "masoquismo",
    "Mecha" to "mecha",
    "Milf" to "milf",
    "Militar" to "militar",
    "Misterio" to "misterio",
    "Netorare" to "netorare",
    "Psicológico" to "psicologico",
    "Realidad" to "realidad",
    "Realidad Virtual" to "realidad-virtual",
    "Recuentos de la vida" to "recuentos-de-la-vida",
    "Reencarnación" to "reencarnacion",
    "Romance" to "romance",
    "Seinen" to "seinen",
    "Shoujo" to "shoujo",
    "Shounen" to "shounen",
    "Slice of Life" to "slice-of-life",
    "Smut" to "smut",
    "Supernatural" to "supernatural",
    "Superpoderes" to "superpoderes",
    "Supervivencia" to "supervivencia",
    "Telenovela" to "telenovela",
    "Thriller" to "thriller",
    "Tragedia" to "tragedia",
    "Venganza" to "venganza",
    "Vida Escolar" to "vida-escolar",
    "Video Juegos" to "video-juegos",
    "Villana" to "villana",
    "Webtoon" to "webtoon",
    "Yaoi" to "yaoi",
    "Yuri" to "yuri",
)
