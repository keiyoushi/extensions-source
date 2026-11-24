package eu.kanade.tachiyomi.extension.vi.hentaibox

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(genres: List<Pair<String, String>>?): FilterList = FilterList(
    listOfNotNull(
        Filter.Header("Filters are ignored when searching by name"),
        SortFilter(),
        StatusFilter(),
        Filter.Header("Status only applies without a genre"),
        genres?.let(::GenreFilter),
    ),
)

open class QueryFilter(name: String, private val options: List<Pair<String, String?>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val value get() = options[state].second
}

class SortFilter :
    QueryFilter(
        "Sắp xếp",
        listOf("Mới cập nhật" to null, "Truyện mới" to "created", "Xem nhiều" to "views", "A → Z" to "az"),
    )

class StatusFilter : QueryFilter("Tình trạng", listOf("Tất cả" to null, "Hoàn thành" to "completed"))

class GenreFilter(genres: List<Pair<String, String>>) : QueryFilter("Thể loại", listOf("Tất cả" to null) + genres)
