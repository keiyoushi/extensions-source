package eu.kanade.tachiyomi.extension.vi.damconuong

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(genres: List<GenreOption>?): FilterList = FilterList(
    buildList {
        add(SortFilter())
        add(StatusFilter())
        add(SearchTypeFilter())
        add(MinRatingFilter())
        add(AiTranslationFilter())
        if (!genres.isNullOrEmpty()) {
            add(GenreFilter(genres.map { Genre(it.name, it.id) }))
        }
    },
)

class SortFilter :
    UriPartFilter(
        "Sắp xếp",
        arrayOf(
            Pair("Mới cập nhật", "-updated_at"),
            Pair("Mới nhất", "-created_at"),
            Pair("Top tuần", "-views_week"),
            Pair("Đánh giá cao nhất", "-average_rating"),
            Pair("Cũ nhất", "created_at"),
            Pair("Xem nhiều", "-views"),
            Pair("Top ngày", "-views_day"),
            Pair("Nhiều lượt đánh giá nhất", "-total_ratings"),
            Pair("Tên A-Z", "name"),
            Pair("Tên Z-A", "-name"),
        ),
    )

class StatusFilter :
    UriPartFilter(
        "Tình trạng",
        arrayOf(
            Pair("Tất cả", ""),
            Pair("Hoàn thành", "1"),
            Pair("Đang tiến hành", "2"),
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

class MinRatingFilter :
    UriPartFilter(
        "Đánh giá tối thiểu",
        arrayOf(
            Pair("Tất cả", ""),
            Pair("1+ ★", "1"),
            Pair("2+ ★", "2"),
            Pair("3+ ★", "3"),
            Pair("4+ ★", "4"),
            Pair("5+ ★", "5"),
        ),
    )

class AiTranslationFilter :
    UriPartFilter(
        "Bản dịch",
        arrayOf(
            Pair("Tất cả", ""),
            Pair("Chỉ truyện AI dịch", "1"),
            Pair("Ẩn truyện AI dịch", "0"),
        ),
    )

class Genre(name: String, val id: Int) : Filter.TriState(name)

class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Thể loại", genres)

open class UriPartFilter(
    displayName: String,
    private val vals: Array<Pair<String, String>>,
    defaultState: Int = 0,
) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), defaultState) {
    fun toUriPart() = vals[state].second
}
