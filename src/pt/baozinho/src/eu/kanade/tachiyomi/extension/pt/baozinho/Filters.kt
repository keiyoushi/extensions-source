package eu.kanade.tachiyomi.extension.pt.baozinho

import eu.kanade.tachiyomi.source.model.Filter

open class SelectFilter(name: String, private val options: List<Pair<String, String>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val value get() = options[state].second
}

// The first item matches getPopularManga, so the filter sheet reflects the open list.
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

class FormatoFilter(formatos: List<FormatoDto>) : SelectFilter("Formato", listOf("Todos" to "") + formatos.map { it.nome to it.slug })

class TagCheckBox(tag: TagDto) : Filter.CheckBox(tag.nome) {
    val id = tag.id
}

// The API combines several tags with OR, not AND.
class TagFilter(tags: List<TagDto>) : Filter.Group<TagCheckBox>("Gêneros (qualquer um dos marcados)", tags.map(::TagCheckBox))
