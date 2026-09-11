package eu.kanade.tachiyomi.extension.vi.moetruyen

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(genres: List<GenreOption>?): FilterList {
    val filters = mutableListOf<Filter<*>>(
        StatusFilter(),
        SortFilter(),
    )
    if (!genres.isNullOrEmpty()) {
        filters += GenreFilter(genres)
    }
    return FilterList(filters)
}

open class UriPartFilter(
    displayName: String,
    private val vals: Array<Pair<String, String>>,
    defaultIndex: Int = 0,
) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), defaultIndex) {
    fun toUriPart(): String = vals[state].second
}

class StatusFilter :
    UriPartFilter(
        "Trạng thái",
        arrayOf(
            "Tất cả" to "",
            "Còn tiếp" to "Còn tiếp",
            "Hoàn thành" to "Hoàn thành",
            "Tạm dừng" to "Tạm dừng",
        ),
    )

class SortFilter :
    UriPartFilter(
        "Sắp xếp theo",
        arrayOf(
            "Mới cập nhật" to "updated_desc",
            "Lượt xem cao nhất" to "views_desc",
            "Lượt xem thấp nhất" to "views_asc",
            "Lượt lưu nhiều nhất" to "bookmarks_desc",
            "Lượt lưu ít nhất" to "bookmarks_asc",
            "Tên truyện A-Z" to "title_asc",
            "Tên truyện Z-A" to "title_desc",
            "Nhiều chương nhất" to "chapters_desc",
            "Ít chương nhất" to "chapters_asc",
            "Bình luận nhiều nhất" to "comments_desc",
            "Bình luận ít nhất" to "comments_asc",
        ),
    )

class GenreTriStateFilter(name: String, val id: String) : Filter.TriState(name)

class GenreFilter(genres: List<GenreOption>) :
    Filter.Group<GenreTriStateFilter>(
        "Thể loại",
        genres.map { GenreTriStateFilter(it.name, it.id) },
    )
