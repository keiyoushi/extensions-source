package eu.kanade.tachiyomi.extension.es.mangako

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

// "Más vistos" is its own list on the site, whose only order is named "Nombre"
class Sort(val label: String, val option: String, val type: String? = null)

val SORTS = listOf(
    Sort("Más vistos", "Nombre", "popular"),
    Sort("Más recientes", "Más recientes"),
    Sort("Más antiguos", "Más antiguos"),
)

class SortFilter : Filter.Select<String>("Ordenar por", SORTS.map { it.label }.toTypedArray()) {
    val sort get() = SORTS[state]
}

fun getFilters() = FilterList(SortFilter())
