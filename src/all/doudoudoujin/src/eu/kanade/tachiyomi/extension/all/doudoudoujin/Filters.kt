package eu.kanade.tachiyomi.extension.all.doudoudoujin

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(): FilterList = FilterList(GenreFilter())

class GenreFilter :
    Filter.Select<String>(
        "Genre",
        (listOf("All") + GENRES.map { it.first }).toTypedArray(),
    ) {
    val id get() = GENRES.getOrNull(state - 1)?.second
}

private val GENRES = listOf(
    "BL" to "bl",
    "R18" to "r18",
    "Shojo" to "shojo",
    "Shonen" to "shonen",
    "TL" to "tl",
)
