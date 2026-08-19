package eu.kanade.tachiyomi.extension.pt.remangas

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UrlFilter {
    fun addToUrl(builder: HttpUrl.Builder)
}

open class SelectFilter(
    name: String,
    private val parameter: String,
    private val options: List<Pair<String, String>>,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()),
    UrlFilter {
    val selectedValue get() = options[state].second

    override fun addToUrl(builder: HttpUrl.Builder) {
        selectedValue.takeIf(String::isNotEmpty)?.let { builder.addQueryParameter(parameter, it) }
    }
}

class SortFilter :
    SelectFilter(
        "Ordenar por",
        "sort",
        listOf(
            "Populares" to "popular",
            "Atualizados recentemente" to "latest",
            "Adicionados recentemente" to "newest",
            "Melhor avaliados" to "rating",
            "Mais seguidos" to "followers",
            "Mais vistos" to "views",
        ),
    )

class TypeFilter :
    SelectFilter(
        "Tipo",
        "type",
        listOf(
            "Todos" to "",
            "Mangá" to "manga",
            "Manhwa" to "manhwa",
            "Manhua" to "manhua",
            "Webtoon" to "webtoon",
            "Pornhwa" to "pornhwa",
            "Novel" to "novel",
        ),
    )

class StatusFilter :
    SelectFilter(
        "Status",
        "status",
        listOf(
            "Todos" to "",
            "Em andamento" to "ongoing",
            "Completo" to "completed",
            "Hiato" to "hiatus",
            "Cancelado" to "cancelled",
        ),
    )

class DemographicFilter :
    SelectFilter(
        "Demografia",
        "demographic",
        listOf(
            "Todas" to "",
            "Shounen" to "shounen",
            "Seinen" to "seinen",
            "Shoujo" to "shoujo",
            "Josei" to "josei",
        ),
    )

class ContentFilter :
    SelectFilter(
        "Conteúdo",
        "adult",
        listOf(
            "Todos" to "",
            "Sem conteúdo adulto" to "false",
            "Apenas conteúdo adulto" to "true",
        ),
    )

class GenreFilter(genres: List<GenreDto>) :
    SelectFilter(
        "Gênero",
        "genre",
        listOf("Todos" to "") + genres.map { it.name to it.slug },
    )
