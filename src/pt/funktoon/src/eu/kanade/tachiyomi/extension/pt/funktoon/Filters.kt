package eu.kanade.tachiyomi.extension.pt.funktoon

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

class CategoryFilter(private val stamps: List<StampDto>) : Filter.Select<String>("Categoria", (listOf("Todas") + stamps.map { it.name }).toTypedArray()) {
    val selected: Int? get() = stamps.getOrNull(state - 1)?.id
}

fun getFilters(stamps: List<StampDto>?): FilterList = if (stamps == null) {
    FilterList(Filter.Header("Toque em \"Redefinir\" para carregar as categorias"))
} else {
    FilterList(
        Filter.Header("A busca por texto ignora a categoria"),
        CategoryFilter(stamps),
    )
}
