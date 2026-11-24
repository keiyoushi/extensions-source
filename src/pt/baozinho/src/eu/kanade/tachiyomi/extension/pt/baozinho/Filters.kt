package eu.kanade.tachiyomi.extension.pt.baozinho

import eu.kanade.tachiyomi.source.model.Filter

open class SelectFilter(name: String, private val options: List<Pair<String, String>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val value get() = options[state].second
}

// O primeiro item é o padrão de getPopularManga, para a tela de filtros refletir a lista aberta.
class SortFilter :
    SelectFilter(
        "Ordenar por",
        listOf(
            "Mais populares" to "populares_total",
            "Populares da semana" to "populares_semana",
            "Melhor avaliadas" to "rating",
            "Atualizadas" to "atualizadas",
            "Recém-adicionadas" to "recentes",
            "A-Z" to "alfabetica_az",
            "Z-A" to "alfabetica_za",
        ),
    )

class StatusFilter :
    SelectFilter(
        "Status",
        listOf(
            "Todos" to "",
            "Em lançamento" to "em_lancamento",
            "Finalizado" to "finalizado",
            "Hiato" to "hiato",
            "Cancelado" to "cancelado",
        ),
    )

class FormatFilter(formats: List<FormatDto>) : SelectFilter("Formato", listOf("Todos" to "") + formats.map { it.name to it.slug })

class TagCheckBox(tag: TagDto) : Filter.CheckBox(tag.name) {
    val id = tag.id
}

// A API combina várias tags por OU, não por E.
class TagFilter(tags: List<TagDto>) : Filter.Group<TagCheckBox>("Gêneros (qualquer um dos marcados)", tags.map(::TagCheckBox))
