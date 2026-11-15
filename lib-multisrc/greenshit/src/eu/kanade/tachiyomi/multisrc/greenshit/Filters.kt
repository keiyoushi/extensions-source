package eu.kanade.tachiyomi.multisrc.greenshit

import eu.kanade.tachiyomi.source.model.Filter

class GenresFilter(genres: List<CheckBoxFilter>) : Filter.Group<CheckBoxFilter>("Gêneros", genres)

class TagsFilter(tags: List<CheckBoxFilter>) : Filter.Group<CheckBoxFilter>("Tags", tags)

class CheckBoxFilter(name: String, val value: String) : Filter.CheckBox(name)

class FormatoFilter(
    options: Array<Pair<String, String>> = FORMATOS,
) : UriSelectFilter("Formato", options) {
    companion object {
        val FORMATOS = arrayOf(
            Pair("Todos", ""),
            Pair("Mangá", "3"),
            Pair("Manhua", "2"),
            Pair("Manhwa", "1"),
        )
    }
}

class StatusFilter(
    options: Array<Pair<String, String>> = STATUS,
) : UriSelectFilter("Status", options) {
    companion object {
        val STATUS = arrayOf(
            Pair("Todos", ""),
            Pair("Cancelado", "4"),
            Pair("Concluído", "2"),
            Pair("Em Andamento", "1"),
            Pair("Hiato", "3"),
        )
    }
}

class SortFilter(
    options: Array<Pair<String, String>> = ORDENAR,
) : UriSelectFilter("Ordenar Por", options, 0) {
    companion object {
        val ORDENAR = arrayOf(
            Pair("Última atualização", "ultima_atualizacao"),
            Pair("Lançamentos", "criacao"),
            Pair("Mais Visualizadas", "visualizacoes_geral"),
            Pair("Melhor avaliação", "rating"),
            Pair("A-Z", "nome"),
        )
    }
}

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
