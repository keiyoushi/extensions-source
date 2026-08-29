package eu.kanade.tachiyomi.extension.vi.goctruyentranhvui

import eu.kanade.tachiyomi.source.model.Filter

class Option(name: String, val id: String) : Filter.CheckBox(name)
open class FilterGroup(name: String, val query: String, state: List<Option>) : Filter.Group<Option>(name, state)

class StatusList(status: List<Option>) : FilterGroup("Trạng Thái", "status[]", status)

fun getStatusList() = listOf(
    Option("Đang thực hiện", "PRG"),
    Option("Hoàn thành", "END"),
    Option("Truyện Chữ", "novel"),
)

class SortByList(sort: List<Option>) : FilterGroup("Sắp xếp", "orders[]", sort)

fun getSortByList() = listOf(
    Option("Lượt xem", "viewCount"),
    Option("Lượt đánh giá", "evaluationScore"),
    Option("Lượt theo dõi", "followerCount"),
    Option("Ngày Cập Nhật", "recentDate"),
    Option("Truyện Mới", "createdAt"),
)

class GenreList(genres: List<Option>) : FilterGroup("Thể loại", "categories[]", genres)
