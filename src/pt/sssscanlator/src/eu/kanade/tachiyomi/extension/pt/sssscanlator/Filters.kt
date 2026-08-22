@file:Suppress("SpellCheckingInspection")

package eu.kanade.tachiyomi.extension.pt.sssscanlator

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
            "Mais populares" to "popular",
            "Maior avaliação" to "rating",
            "Atualizados recentemente" to "recent",
            "Novidades" to "new",
            "A-Z" to "alphabetical",
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
            "Novel" to "novel",
            "Yaoi" to "yaoi",
            "Yuri" to "yuri",
            "Shoujo" to "shoujo",
            "English" to "english",
            "Webtoon" to "webtoon",
            "Doujinshi" to "doujinshi",
            "Hentai" to "hentai",
            "Pornhwa" to "pornhwa",
        ),
    )

class StatusFilter :
    SelectFilter(
        "Status",
        "status",
        listOf(
            "Todos" to "",
            "Em lançamento" to "ONGOING",
            "Completo" to "COMPLETED",
            "Hiato" to "HIATUS",
            "Cancelado" to "CANCELED",
        ),
    )

class GenreFilter(genres: List<String>) :
    SelectFilter(
        "Gênero",
        "genre",
        listOf("Todos" to "") + genres.map { it to it },
    )
