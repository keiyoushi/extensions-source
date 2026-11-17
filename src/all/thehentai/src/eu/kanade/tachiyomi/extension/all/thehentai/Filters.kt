package eu.kanade.tachiyomi.extension.all.thehentai

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(): FilterList = FilterList(
    Filter.Header("Text search ignores the category"),
    CategoryFilter(),
)

class CategoryFilter :
    Filter.Select<String>(
        "Category",
        (listOf("All") + CATEGORIES.map { it.first }).toTypedArray(),
    ) {
    val slug get() = CATEGORIES.getOrNull(state - 1)?.second
}

private val CATEGORIES = listOf(
    "Hentai" to "hentai",
    "Comics" to "comics-hq",
    "Exclusive" to "exclusivo",
    "Images" to "imagens",
    "3D" to "3d",
)
