package eu.kanade.tachiyomi.extension.es.ritto

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.HttpUrl

open class UriPartFilter(
    name: String,
    private val parameter: String,
    private val options: List<Pair<String, String>>,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    fun addTo(url: HttpUrl.Builder) {
        options[state].second.takeIf { it.isNotEmpty() }?.let { url.addQueryParameter(parameter, it) }
    }
}

class SortFilter :
    UriPartFilter(
        "Ordenar por",
        "orden",
        listOf(
            "Populares" to "vistas",
            "Más recientes" to "reciente",
            "Más favoritas" to "favoritos",
            "Título A-Z" to "titulo",
        ),
    )

class TypeFilter :
    UriPartFilter(
        "Tipo",
        "tipo",
        listOf(
            "Todos" to "",
            "Manga" to "MANGA",
            "Manhua" to "MANHUA",
            "Manhwa" to "MANHWA",
            "One shot" to "ONE_SHOT",
            "Doujinshi" to "DOUJINSHI",
            "Oel" to "OEL",
            "Otro" to "OTRO",
        ),
    )

class StatusFilter :
    UriPartFilter(
        "Estado",
        "estado",
        listOf(
            "Todos" to "",
            "En emisión" to "EN_EMISION",
            "Finalizado" to "FINALIZADO",
            "Pausado" to "PAUSADO",
            "Cancelado" to "CANCELADO",
        ),
    )

class GenreFilter :
    UriPartFilter(
        "Género",
        "genero",
        listOf(
            "Todos" to "",
            "Acción" to "accion",
            "Animación" to "animacion",
            "Apocalíptico" to "apocaliptico",
            "Artes Marciales" to "artes-marciales",
            "Aventura" to "aventura",
            "Boys Love" to "boys-love",
            "Ciberpunk" to "ciberpunk",
            "Ciencia Ficción" to "ciencia-ficcion",
            "Comedia" to "comedia",
            "Crimen" to "crimen",
            "Demonios" to "demonios",
            "Deporte" to "deporte",
            "Drama" to "drama",
            "Ecchi" to "ecchi",
            "Extranjero" to "extranjero",
            "Familia" to "familia",
            "Fantasia" to "fantasia",
            "Girls Love" to "girls-love",
            "Gore" to "gore",
            "Guerra" to "guerra",
            "Género Bender" to "genero-bender",
            "Harem" to "harem",
            "Historia" to "historia",
            "Horror" to "horror",
            "Magia" to "magia",
            "Mecha" to "mecha",
            "Militar" to "militar",
            "Misterio" to "misterio",
            "Musica" to "musica",
            "Niños" to "ninos",
            "Oeste" to "oeste",
            "Original" to "original",
            "Parodia" to "parodia",
            "Policiaco" to "policiaco",
            "Psicológico" to "psicologico",
            "Realidad" to "realidad",
            "Realidad Virtual" to "realidad-virtual",
            "Recuentos de la vida" to "recuentos-de-la-vida",
            "Reencarnación" to "reencarnacion",
            "Romance" to "romance",
            "Samurái" to "samurai",
            "Sobrenatural" to "sobrenatural",
            "Superpoderes" to "superpoderes",
            "Supervivencia" to "supervivencia",
            "Telenovela" to "telenovela",
            "Thriller" to "thriller",
            "Tragedia" to "tragedia",
            "Traps" to "traps",
            "Vampiros" to "vampiros",
            "Vida Escolar" to "vida-escolar",
            "Yaoi" to "yaoi",
            "Yuri" to "yuri",
        ),
    )

class CategoryFilter :
    UriPartFilter(
        "Categoría",
        "categoria",
        listOf(
            "Todas" to "",
            "Originales" to "originales",
            "Seinen" to "seinen",
            "Shoujo" to "shoujo",
            "Shounen" to "shounen",
            "Josei" to "josei",
            "Kodomo" to "kodomo",
        ),
    )

fun getFilters() = FilterList(
    SortFilter(),
    TypeFilter(),
    StatusFilter(),
    GenreFilter(),
    CategoryFilter(),
)
