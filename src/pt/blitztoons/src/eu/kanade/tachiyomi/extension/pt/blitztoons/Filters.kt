package eu.kanade.tachiyomi.extension.pt.blitztoons

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(genres: List<Pair<String, String>>?): FilterList = FilterList(
    listOfNotNull(
        SortFilter(),
        TypeFilter(),
        StatusFilter(),
        genres?.let(::GenreFilter),
    ),
)

open class QueryFilter(name: String, val param: String, private val options: List<Pair<String, String?>>, state: Int = 0) : Filter.Select<String>(name, options.map { it.first }.toTypedArray(), state) {
    val value get() = options[state].second
}

class SortFilter :
    QueryFilter(
        "Ordenar",
        "ordem",
        listOf("Recentes" to "recentes", "Populares" to "populares", "Melhor nota" to "nota", "A-Z" to "alfabetica"),
        state = 1,
    )

class TypeFilter :
    QueryFilter(
        "Categoria",
        "tipo",
        listOf("Todos" to null, "Comics" to "comic", "Yaoi" to "yaoi", "Yuri" to "yuri", "Shoujo" to "shoujo"),
    )

class StatusFilter :
    QueryFilter(
        "Status",
        "status",
        listOf("Todos" to null, "Em lançamento" to "ongoing", "Completo" to "completed", "Hiato" to "hiatus"),
    )

class GenreFilter(genres: List<Pair<String, String>>) : QueryFilter("Gênero", "genero", listOf("Todos" to null) + genres)
