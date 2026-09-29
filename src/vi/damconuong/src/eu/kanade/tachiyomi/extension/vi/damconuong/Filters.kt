package eu.kanade.tachiyomi.extension.vi.damconuong

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.serialization.Serializable

fun getFilters(genres: List<GenreOption>?): FilterList = FilterList(
    buildList {
        add(SortFilter())
        add(StatusFilter())
        add(SearchTypeFilter())
        add(MinRatingFilter())
        genres?.takeIf { it.isNotEmpty() }?.let {
            add(GenreFilter(it.map { genre -> Genre(genre.name, genre.id) }))
        }
    },
)

class SortFilter :
    UriPartFilter(
        "Sắp xếp",
        arrayOf(
            Pair("Mới cập nhật", "-updated_at"),
            Pair("Mới thêm", "-created_at"),
            Pair("Cũ nhất", "created_at"),
            Pair("Lượt xem", "-views"),
            Pair("Xem trong ngày", "-views_day"),
            Pair("Xem trong tuần", "-views_week"),
            Pair("Đánh giá", "-average_rating"),
            Pair("A-Z", "name"),
            Pair("Z-A", "-name"),
        ),
    )

class StatusFilter :
    UriPartFilter(
        "Trạng thái",
        arrayOf(
            Pair("Tất cả", ""),
            Pair("Đang tiến hành", "2"),
            Pair("Hoàn thành", "1"),
        ),
    )

class SearchTypeFilter :
    UriPartFilter(
        "Tìm theo",
        arrayOf(
            Pair("Tên truyện", "name"),
            Pair("Tác giả / Họa sĩ", "artist"),
            Pair("Tác giả", "author"),
        ),
    )

class MinRatingFilter :
    UriPartFilter(
        "Đánh giá tối thiểu",
        arrayOf(
            Pair("Tất cả", ""),
            Pair("Từ 3 sao", "3"),
            Pair("Từ 4 sao", "4"),
            Pair("Từ 4.5 sao", "4.5"),
        ),
    )

class Genre(name: String, val id: Int) : Filter.TriState(name)

class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Thể loại", genres)

open class UriPartFilter(
    displayName: String,
    private val vals: Array<Pair<String, String>>,
) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

@Serializable
class FilterData(val genres: List<GenreOption> = emptyList())
