package eu.kanade.tachiyomi.extension.pt.slimereadunoriginal

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UrlPartFilter {
    fun addToUrl(builder: HttpUrl.Builder)
}

class GenreFilter :
    Filter.Text("Gênero/Tema"),
    UrlPartFilter {
    override fun addToUrl(builder: HttpUrl.Builder) {
        if (state.isNotBlank()) {
            builder.addQueryParameter("genre", state.trim())
        }
    }
}

open class SelectFilter(
    name: String,
    private val param: String,
    private val options: List<Pair<String, String>>,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()),
    UrlPartFilter {
    override fun addToUrl(builder: HttpUrl.Builder) {
        val value = options[state].second
        if (value.isNotBlank()) {
            builder.addQueryParameter(param, value)
        }
    }
}

class TypeFilter :
    SelectFilter(
        "Tipo",
        "type",
        listOf(
            "Todos" to "",
            "Mangá" to "manga",
            "Manhwa" to "manhwa",
            "Manhua" to "manhua",
            "Hentai" to "hentai",
        ),
    )

class StatusFilter :
    SelectFilter(
        "Status",
        "status",
        listOf(
            "Todos" to "",
            "Em andamento" to "em andamento",
            "Completo" to "completo",
            "Hiato" to "hiato",
            "Cancelado" to "cancelado",
        ),
    )

class AdultFilter :
    SelectFilter(
        "Conteúdo +18",
        "adult",
        listOf(
            "Todos" to "",
            "Ocultar +18" to "hide",
            "Somente +18" to "only",
        ),
    )

class SortFilter :
    SelectFilter(
        "Ordenar por",
        "sort",
        listOf(
            "Mais recentes" to "",
            "Mais populares" to "popular",
            "Título A-Z" to "title_asc",
            "Título Z-A" to "title_desc",
        ),
    )
