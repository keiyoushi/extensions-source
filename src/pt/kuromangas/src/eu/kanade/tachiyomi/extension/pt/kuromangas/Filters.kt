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
    "Melhor Avaliados" to "average_rating",
    "Mais Antigos" to "created_at",
    "Titulo (A-Z)" to "title",
    "Titulo (Z-A)" to "title",
)
