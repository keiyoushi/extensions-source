package eu.kanade.tachiyomi.extension.vi.kirakira

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

class GenreFilter(private val genres: List<GenreOption>) :
    Filter.Select<String>(
        "Thể loại",
        genres.map { it.name }.toTypedArray(),
    ) {
    val selected: GenreOption
        get() = genres[state]
}

fun getFilters(genres: List<GenreOption>): FilterList {
    val filters = mutableListOf<Filter<*>>()
    if (genres.isNotEmpty()) {
        filters += Filter.Header("Thể loại")
        filters += GenreFilter(listOf(GenreOption("Tất cả", "all")) + genres)
    }
    filters += SortFilter()
    filters += StatusFilter()
    return FilterList(filters)
}

class GenreOption(
    val name: String,
    val id: String? = null,
)

class SortFilter :
    UriPartFilter(
        "Sắp xếp",
        arrayOf(
            "Mới cập nhật" to null,
            "Lượt xem" to "views",
            "Mới thêm" to "new",
        ),
    )

class StatusFilter :
    UriPartFilter(
        "Trạng thái",
        arrayOf(
            "Tất cả" to null,
            "Đang ra" to "updating",
            "Hoàn thành" to "completed",
        ),
    )

open class UriPartFilter(
    displayName: String,
    private val options: Array<Pair<String, String?>>,
) : Filter.Select<String>(displayName, options.map { it.first }.toTypedArray()) {
    val selected: String?
        get() = options[state].second
}
