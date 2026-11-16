package eu.kanade.tachiyomi.extension.pt.xxxyaoi

import eu.kanade.tachiyomi.source.model.Filter

open class SelectFilter(name: String, private val options: List<Pair<String, String>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val value get() = options[state].second
}

class SortFilter :
    SelectFilter(
        "Ordenar por",
        listOf("Mais recentes" to "latest", "Novidades" to "new-manga", "A–Z" to "alphabet"),
    )

class StatusFilter :
    SelectFilter(
        "Status",
        listOf(
            "Todas" to "",
            "Em Andamento" to "on-going",
            "Concluídas" to "completed",
            "Projetos 3X" to "xxx",
            "Animações" to "animacoes",
        ),
    )

class GenreFilter(genres: List<Genre>) : SelectFilter("Gêneros", listOf("Todos" to "") + genres.map { it.name to it.slug })
