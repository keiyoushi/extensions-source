package eu.kanade.tachiyomi.extension.vi.damconuong

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(genres: List<GenreDto>?): FilterList = FilterList(
    buildList {
        add(SortFilter())
        add(StatusFilter())
        add(SearchTypeFilter())
        genres?.let { add(GenreFilter(it.map { genre -> Genre(genre.name, genre.id) })) }
    },
)

class SortFilter :
    UriPartFilter(
        "Sắp xếp",
        arrayOf(
            Pair("Mới cập nhật", "-updated_at"),
            Pair("Mới thêm", "-created_at"),
            Pair("Cũ nhất", "created_at"),
            Pair("Xem nhiều", "-views"),
            Pair("Top ngày", "-views_day"),
            Pair("Top tuần", "-views_week"),
            Pair("Đánh giá", "-average_rating"),
            Pair("A-Z", "name"),
            Pair("Z-A", "-name"),
        ),
    )

class StatusFilter :
    UriPartFilter(
        "Trạng thái",
        arrayOf(
            Pair("Đang tiến hành + Hoàn thành", "2,1"),
            Pair("Đang tiến hành", "2"),
            Pair("Hoàn thành", "1"),
        ),
    )

class SearchTypeFilter :
    UriPartFilter(
        "Tìm theo",
        arrayOf(
            Pair("Tên truyện", "name"),
            Pair("Tác giả", "author"),
            Pair("Họa sĩ", "artist"),
        ),
    )

class Genre(name: String, val id: Int) : Filter.CheckBox(name)

class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Thể loại", genres)

open class UriPartFilter(
    displayName: String,
    private val vals: Array<Pair<String, String>>,
) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}
