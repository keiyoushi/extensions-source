package eu.kanade.tachiyomi.extension.vi.leesincomic

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.serialization.Serializable

@Serializable
class FilterData(
    val genres: List<String> = emptyList(),
)

fun buildFilterList(data: FilterData?): FilterList {
    val filters = mutableListOf<Filter<*>>()

    val genres = data?.genres.orEmpty()
    if (genres.isNotEmpty()) {
        filters += GenreFilter(genres)
    }

    filters += StatusFilter()
    filters += SortFilter()

    return FilterList(filters)
}

class GenreFilter(genres: List<String>) :
    Filter.Select<String>(
        "Thể loại",
        (listOf("Tất cả") + genres).toTypedArray(),
    ) {
    private val genreNames = listOf("") + genres

    val selected: String? get() = genreNames.getOrNull(state)?.takeIf { it.isNotEmpty() }
}

class StatusFilter :
    Filter.Select<String>(
        "Tình trạng",
        arrayOf("Tất cả", "Đang ra", "Hoàn thành"),
    )

class SortFilter :
    Filter.Select<String>(
        "Sắp xếp",
        arrayOf("Lượt xem nhiều nhất", "Yêu thích nhất", "Đánh giá cao nhất", "Mới cập nhật"),
    )
