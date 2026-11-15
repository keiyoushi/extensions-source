package eu.kanade.tachiyomi.extension.ar.mangaar

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

open class SelectFilter(name: String, private val options: List<Pair<String, String?>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val value get() = options[state].second
}

class TypeFilter : SelectFilter("النوع", TYPES)

class StatusFilter : SelectFilter("الحالة", STATUSES)

class GenreFilter(genres: List<String>) : SelectFilter("التصنيف", listOf("الكل" to null) + genres.map { it to it })

fun getFilters(genres: List<String>?) = FilterList(
    listOfNotNull(
        TypeFilter(),
        StatusFilter(),
        genres?.let(::GenreFilter),
    ),
)

private val TYPES = listOf(
    "الكل" to null,
    "مانغا" to "manga",
    "مانهوا" to "manhwa",
    "مانها" to "manhua",
)

private val STATUSES = listOf(
    "الكل" to null,
    "مستمرة" to "active",
    "منتهية" to "finished",
    "متوقفة" to "hiatus",
    "ملغية" to "cancelled",
)
