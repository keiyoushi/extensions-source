package eu.kanade.tachiyomi.extension.vi.lorangeteam

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(genres: List<Pair<String, String>>?): FilterList = FilterList(
    listOfNotNull(
        genres?.let(::GenreFilter),
        TypeFilter(),
        StatusFilter(),
    ),
)

abstract class QueryFilter(name: String, val param: String, private val options: List<Pair<String, String>>) : Filter.Select<String>(name, (listOf("Tất cả") + options.map { it.first }).toTypedArray()) {
    val value get() = options.getOrNull(state - 1)?.second
}

class GenreFilter(genres: List<Pair<String, String>>) : QueryFilter("Thể loại", "genre", genres)

class TypeFilter : QueryFilter("Loại truyện", "type", listOf("Manhwa" to "manhwa", "Manga" to "manga"))

class StatusFilter : QueryFilter("Trạng thái", "status", listOf("Ongoing" to "ongoing", "Hoàn thành" to "completed", "Hiatus" to "hiatus"))
