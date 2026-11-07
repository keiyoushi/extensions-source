package eu.kanade.tachiyomi.extension.es.comics18

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters() = FilterList(
    Filter.Header("Los filtros se ignoran en la búsqueda por texto"),
    CategoryFilter(),
)

class CategoryFilter : Filter.Select<String>("Categoría", CATEGORIES.map { it.first }.toTypedArray()) {
    fun selectedPath() = CATEGORIES[state].second
}

private val CATEGORIES = listOf(
    "Todas" to null,
    "3D" to "3d",
    "Dibujos" to "dibujos",
    "Dragon Ball" to "dragon-ball",
    "Furry" to "furry",
    "Futanari" to "futanari",
    "Gays" to "gays",
    "Incesto" to "de-incesto",
    "Los Simpson" to "simpson",
    "Manga Hentai" to "hentai",
    "Pokemon" to "pokemon",
)
