package eu.kanade.tachiyomi.extension.es.manhwaonline

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UrlFilter {
    fun addToUrl(url: HttpUrl.Builder)
}

class SortFilter(selection: Filter.Sort.Selection = POPULAR) :
    Filter.Sort("Ordenar por", SORTS.map { it.first }.toTypedArray(), selection),
    UrlFilter {
    override fun addToUrl(url: HttpUrl.Builder) {
        val selection = state ?: return
        url.addQueryParameter("m_orderby", SORTS[selection.index].second)
        if (selection.ascending) url.addQueryParameter("order", "asc")
    }

    companion object {
        private val SORTS = listOf(
            "Actualización" to "latest",
            "Más vistos" to "views",
            "Mejor valorados" to "rating",
            "Título" to "alphabet",
            "Nº de capítulos" to "chapters",
            "Año" to "year",
            "Relevancia" to "relevance",
        )
        val LATEST = Filter.Sort.Selection(0, false)
        val POPULAR = Filter.Sort.Selection(1, false)
    }
}

open class SelectFilter(
    name: String,
    private val param: String,
    private val options: List<Pair<String, String>>,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()),
    UrlFilter {
    override fun addToUrl(url: HttpUrl.Builder) {
        val value = options[state].second
        if (value.isNotEmpty()) url.addQueryParameter(param, value)
    }
}

class FieldFilter :
    SelectFilter(
        "Buscar en",
        "field",
        listOf(
            "Todo" to "",
            "Título" to "title",
            "Autor" to "author",
            "Artista" to "artist",
        ),
    )

class GenreModeFilter :
    SelectFilter(
        "Condición de los géneros incluidos",
        "op",
        listOf(
            "Cualquiera" to "",
            "Todos" to "1",
        ),
    )

class AdultFilter :
    SelectFilter(
        "Contenido +18",
        "adult",
        listOf(
            "Todo" to "",
            "Sin +18" to "0",
            "Solo +18" to "1",
        ),
    )

class CheckBoxOption(name: String, val value: String) : Filter.CheckBox(name)

open class CheckBoxGroup(
    name: String,
    private val param: String,
    options: List<Pair<String, String>>,
) : Filter.Group<CheckBoxOption>(name, options.map { CheckBoxOption(it.first, it.second) }),
    UrlFilter {
    override fun addToUrl(url: HttpUrl.Builder) {
        state.filter { it.state }.forEach { url.addQueryParameter(param, it.value) }
    }
}

class TypeFilter :
    CheckBoxGroup(
        "Tipo",
        "type[]",
        listOf(
            "Manhwa" to "manhwa",
            "Manhua" to "manhua",
            "Manga" to "manga",
            "Novela" to "novel",
        ),
    )

class StatusFilter :
    CheckBoxGroup(
        "Estado",
        "status[]",
        listOf(
            "En emisión" to "on-going",
            "Completo" to "end",
            "En pausa" to "on-hold",
            "Cancelado" to "canceled",
        ),
    )

class DemographicFilter :
    CheckBoxGroup(
        "Demografía",
        "tag[]",
        listOf(
            "Josei" to "josei",
            "Seinen" to "seinen",
            "Shoujo" to "shoujo",
            "Shounen" to "shounen",
        ),
    )

class YearFilter(name: String, private val param: String) :
    Filter.Text(name),
    UrlFilter {
    override fun addToUrl(url: HttpUrl.Builder) {
        if (state.isNotBlank()) url.addQueryParameter(param, state.trim())
    }
}

class GenreOption(name: String, val value: String) : Filter.TriState(name)

class GenreFilter :
    Filter.Group<GenreOption>("Géneros", GENRES.map { GenreOption(it.first, it.second) }),
    UrlFilter {
    override fun addToUrl(url: HttpUrl.Builder) {
        state.forEach {
            when {
                it.isIncluded() -> url.addQueryParameter("genre[]", it.value)
                it.isExcluded() -> url.addQueryParameter("genre_ex[]", it.value)
            }
        }
    }
}

private val GENRES = listOf(
    "+18" to "manhwa-18",
    "Acción" to "manhwa-accion",
    "Adulto" to "manhwa-adulto",
    "Apocalíptico" to "manhwa-apocaliptico",
    "Artes Marciales" to "manhwa-artes-marciales",
    "Aventura" to "manhwa-aventura",
    "Boys Love" to "manhwa-bl",
    "Ciencia Ficción" to "manhwa-ciencia-ficcion",
    "Comedia" to "manhwa-comedia",
    "Cosas de la vida" to "manhwa-cosas-de-la-vida",
    "Crimen" to "manhwa-crimen",
    "Demonios" to "manhwa-demonios",
    "Deporte" to "manhwa-deporte",
    "Detective" to "manhwa-detective",
    "Diferencia de edad" to "manhwa-diferencia-de-edad",
    "Doujinshi" to "manhwa-doujinshi",
    "Drama" to "manhwa-drama",
    "Ecchi" to "manhwa-ecchi",
    "Familia" to "manhwa-familia",
    "Fantasía" to "manhwa-fantasia",
    "Furros" to "manhwa-furros",
    "Girls Love" to "manhwa-gl",
    "Guerra" to "manhwa-guerra",
    "Guideverse" to "manhwa-guideverse",
    "Harem" to "manhwa-harem",
    "Historia" to "manhwa-historia",
    "Horror" to "manhwa-horror",
    "Isekai" to "manhwa-isekai",
    "Magia" to "manhwa-magia",
    "Misterio" to "manhwa-misterio",
    "Murim" to "manhwa-murim",
    "Música" to "manhwa-musica",
    "Niños" to "manhwa-ninos",
    "Oficina" to "manhwa-oficina",
    "Omegaverse" to "manhwa-omegaverse",
    "One shot" to "manhwa-one-shot",
    "Policiaco" to "manhwa-policiaco",
    "Psicológico" to "manhwa-psicologico",
    "Realidad" to "manhwa-realidad",
    "Realidad virtual" to "manhwa-realidad-virtual",
    "Recuentos de la vida" to "manhwa-recuentos-de-la-vida",
    "Reencarnación" to "manhwa-reencarnacion",
    "Regresión" to "manhwa-regresion",
    "Romance" to "manhwa-romance",
    "Sistema" to "manhwa-sistema",
    "Sobrenatural" to "manhwa-sobrenatural",
    "Superpoderes" to "manhwa-superpoderes",
    "Supervivencia" to "manhwa-supervivencia",
    "Thriller" to "manhwa-thriller",
    "Tragedia" to "manhwa-tragedia",
    "Vampiros" to "manhwa-vampiros",
    "Vida Escolar" to "manhwa-vida-escolar",
    "Yaoi" to "manhwa-yaoi",
    "Yuri" to "manhwa-yuri",
)
