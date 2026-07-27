package eu.kanade.tachiyomi.extension.vi.yurigarden

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(genres: List<Pair<String, String>> = emptyList()) = FilterList(
    buildList {
        add(StatusFilter())
        add(SortFilter())
        if (genres.isNotEmpty()) add(GenreFilter(genres))
        add(SearchByFilter())
    },
)

class GenreFilter(genres: List<Pair<String, String>>) :
    Filter.Group<CheckBoxFilter>(
        "Thể loại",
        genres.map { CheckBoxFilter(it.first, it.second, false) },
    )

class SearchByFilter :
    Filter.Group<CheckBoxFilter>(
        "Tìm kiếm theo",
        searchByOptions.map { CheckBoxFilter(it.first, it.second, true) },
    )

open class CheckBoxFilter(name: String, val value: String, state: Boolean) : Filter.CheckBox(name, state)

class StatusFilter :
    Filter.Select<String>(
        "Trạng thái",
        statuses.map { it.first }.toTypedArray(),
    ) {
    val slug get() = statuses[state].second
}

class SortFilter :
    Filter.Select<String>(
        "Sắp xếp theo",
        sorts.map { it.first }.toTypedArray(),
    ) {
    val slug get() = sorts[state].second
}

private val statuses = arrayOf(
    Pair("Tất cả", ""),
    Pair("Sắp ra mắt", "upcoming"),
    Pair("Đang tiến hành", "ongoing"),
    Pair("Đã hoàn thành", "completed"),
    Pair("Tạm dừng", "hiatus"),
    Pair("Đã hủy bỏ", "canceled"),
    Pair("Có yêu cầu xóa", "delete-requested"),
    Pair("Có yêu cầu gộp", "merge-requested"),
)

private val sorts = arrayOf(
    Pair("Mới nhất", "newest"),
    Pair("Cũ nhất", "oldest"),
)

private val searchByOptions = arrayOf(
    Pair("Tiêu đề", "title"),
    Pair("Tên khác", "anotherNames"),
    Pair("Tác giả", "authors"),
    Pair("Họa sĩ", "artists"),
    Pair("Mô tả", "description"),
)
