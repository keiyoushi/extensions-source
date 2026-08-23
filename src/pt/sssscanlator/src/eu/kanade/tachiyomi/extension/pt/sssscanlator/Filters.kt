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
            "Mais recentes" to "newest",
            "Mais populares" to "popular",
            "Melhor nota" to "rating",
            "A-Z" to "az",
        ),
    )

class TypeFilter :
    SelectFilter(
        "Tipo",
        "type",
        listOf(
            "Todos" to "",
            "Mangá" to "MANGA",
            "Manhwa" to "MANHWA",
            "Manhua" to "MANHUA",
            "Novel" to "NOVEL",
            "Yaoi" to "YAOI",
            "Yuri" to "YURI",
            "Shoujo" to "SHOUJO",
            "English" to "ENGLISH",
            "Webtoon" to "WEBTOON",
            "Doujinshi" to "DOUJINSHI",
            "Hentai" to "HENTAI",
            "Pornhwa" to "PORNHWA",
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
