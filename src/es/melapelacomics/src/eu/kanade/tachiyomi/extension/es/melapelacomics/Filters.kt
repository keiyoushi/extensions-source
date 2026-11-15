package eu.kanade.tachiyomi.extension.es.melapelacomics

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

class CategoryFilter : Filter.Select<String>("Categoría", arrayOf("Todas") + CATEGORIES.map { it.first }) {
    val selected: String? get() = CATEGORIES.getOrNull(state - 1)?.second
}

private val CATEGORIES = listOf(
    "Cartoons" to "cartoons",
    "Comics porno 3D" to "comics-porno-3d",
    "Eroticos" to "eroticos",
    "Hentai" to "hentai",
    "Incesto" to "incesto",
    "Interraciales" to "interraciales",
    "Otros estilos" to "otros-estilos",
)

fun getFilters() = FilterList(CategoryFilter())
