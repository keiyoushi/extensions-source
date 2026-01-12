package eu.kanade.tachiyomi.extension.pt.mediocretoons

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilterList() = FilterList(
    FormatFilter(),
    StatusFilter(),
    SortFilter(),
)

class FormatFilter : UriSelectFilter(
    "Formato",
    arrayOf(
        Pair("Todos", ""),
        Pair("Novel", "3"),
        Pair("Shoujo", "4"),
        Pair("Comic", "5"),
        Pair("Yaoi", "8"),
        Pair("Yuri", "9"),
        Pair("Hentai", "10"),
    ),
)

class StatusFilter : UriSelectFilter(
    "Status",
    arrayOf(
        Pair("Todos", ""),
        Pair("Ativo", "1"),
        Pair("Em Andamento", "2"),
        Pair("Cancelada", "3"),
        Pair("Concluído", "4"),
        Pair("Hiato", "6"),
    ),
)

class SortFilter : UriSelectFilter(
    "Ordenar Por",
    arrayOf(
        Pair("Mais Recentes", "criada_em_desc"),
        Pair("Mais Populares", "view_geral"),
        Pair("A-Z", "nome"),
    ),
    defaultValue = 0,
)

open class UriSelectFilter(
    displayName: String,
    private val options: Array<Pair<String, String>>,
    defaultValue: Int = 0,
) : Filter.Select<String>(
    displayName,
    options.map { it.first }.toTypedArray(),
    defaultValue,
) {
    val selected get() = options[state].second
}
