package eu.kanade.tachiyomi.extension.pt.kuromangas

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters() = FilterList(
    SortFilter("Ordenar por", getSortOptions()),
)

class SortFilter(displayName: String, private val sortOptions: List<Pair<String, String>>) : Filter.Select<String>(displayName, sortOptions.map { it.first }.toTypedArray()) {
    val selectedSort: String
        get() = sortOptions.getOrNull(state)?.second ?: "average_rating"
    val selectedOrder: String
        get() = when (state) {
            1 -> "ASC"
            2 -> "ASC"
            else -> "DESC"
        }
}

private fun getSortOptions() = listOf(
    Pair("Melhor Avaliados", "average_rating"),
    Pair("Mais Antigos", "created_at"),
    Pair("Titulo (A-Z)", "title"),
    Pair("Titulo (Z-A)", "title"),
)
