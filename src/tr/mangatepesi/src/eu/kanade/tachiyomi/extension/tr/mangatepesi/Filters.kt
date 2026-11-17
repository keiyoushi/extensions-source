package eu.kanade.tachiyomi.extension.tr.mangatepesi

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters() = FilterList(
    SortFilter(),
    TypeFilter(),
)

class SortFilter : Filter.Select<String>("Sırala", SORTS.map { it.first }.toTypedArray(), 1) {
    val value get() = SORTS[state].second
}

class TypeFilter : Filter.Select<String>("Tür", TYPES.map { it.first }.toTypedArray()) {
    val value get() = TYPES[state].second
}

private val SORTS = listOf(
    "Varsayılan" to "normal-siralama",
    "Okunma ↓" to "azalan-siralama",
    "Okunma ↑" to "artan-siralama",
)

private val TYPES = listOf(
    "Hepsi" to "tum-turler",
    "Webtoon" to "webtoon",
    "Manhwa" to "manhwa",
    "Manhua" to "manhua",
    "Manga" to "manga",
)
