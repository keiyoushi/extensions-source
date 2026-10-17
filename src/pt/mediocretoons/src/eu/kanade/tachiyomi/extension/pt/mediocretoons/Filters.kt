package eu.kanade.tachiyomi.extension.pt.mediocretoons

import eu.kanade.tachiyomi.source.model.Filter

internal class FormatFilter :
    UriPartFilter(
        "Formato",
        arrayOf(
            "Todos" to "",
            "Shoujo" to "4",
            "Comic" to "5",
            "Yaoi" to "8",
            "Yuri" to "9",
            "Hentai" to "10",
        ),
    )

internal class StatusFilter :
    UriPartFilter(
        "Status",
        arrayOf(
            "Todos" to "",
            "Em lançamento" to "1",
            "Finalizado" to "2",
            "Hiato" to "3",
            "Cancelado" to "4",
        ),
    )

internal class SortFilter :
    UriPartFilter(
        "Ordenar por",
        arrayOf(
            "Mais recentes" to "criada_em_desc",
            "Mais populares" to "view_geral",
            "A-Z" to "nome",
        ),
    )

internal open class UriPartFilter(
    displayName: String,
    private val options: Array<Pair<String, String>>,
) : Filter.Select<String>(displayName, options.map { it.first }.toTypedArray()) {
    val selected get() = options[state].second
}
