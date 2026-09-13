package eu.kanade.tachiyomi.extension.pt.mangalivreorg

import eu.kanade.tachiyomi.source.model.Filter

open class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val selectedValue get() = options[state].second
}

class SortFilter :
    SelectFilter(
        "Ordenar por",
        listOf(
            "Atualizações" to "updates",
            "Mais lidos" to "views",
            "Melhores notas" to "rating",
            "Número de capítulos" to "chapters",
            "Finalizados" to "completed",
            "Favoritos" to "favorites",
        ),
    )

class PeriodFilter :
    SelectFilter(
        "Período (apenas para Mais lidos)",
        listOf(
            "Desde o começo" to "ever",
            "Dia" to "day",
            "Semana" to "week",
            "Mês" to "month",
        ),
    )

class CategoryFilter(options: List<GenreDto>) :
    SelectFilter(
        "Categoria (ignora busca e ordenação)",
        listOf("Todas" to "") + options.map { it.name to it.slug },
    )
