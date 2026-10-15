package eu.kanade.tachiyomi.extension.es.emperorscan

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter : Filter.Select<String>("Ordenar por", SORTS.map { it.first }.toTypedArray()) {
    val value get() = SORTS[state].second
}

class GenreFilter(private val genres: List<NamedDto>) : Filter.Select<String>("Género", (listOf("Todos") + genres.map { it.name }).toTypedArray()) {
    val slug get() = genres.getOrNull(state - 1)?.slug
}

private val SORTS = listOf(
    "Recientes" to "reciente",
    "Nuevas" to "nuevas",
    "Más vistas" to "vistas",
    "Mejor valoradas" to "valoradas",
    "Más seguidas" to "seguidas",
    "A-Z" to "alfabetico",
)
