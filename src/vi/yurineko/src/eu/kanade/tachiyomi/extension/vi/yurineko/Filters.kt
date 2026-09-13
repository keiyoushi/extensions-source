package eu.kanade.tachiyomi.extension.vi.yurineko

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(data: FilterData?): FilterList {
    val filters = mutableListOf<Filter<*>>()
    data?.tags?.takeIf { it.isNotEmpty() }?.let { filters += TagFilter(it) }
    data?.authors?.takeIf { it.isNotEmpty() }?.let { filters += AuthorFilter(it) }
    data?.artists?.takeIf { it.isNotEmpty() }?.let { filters += ArtistFilter(it) }
    data?.doujins?.takeIf { it.isNotEmpty() }?.let { filters += DoujinFilter(it) }
    data?.groups?.takeIf { it.isNotEmpty() }?.let { filters += GroupFilter(it) }
    data?.couples?.takeIf { it.isNotEmpty() }?.let { filters += CoupleFilter(it) }
    filters += SortFilter()
    return FilterList(filters)
}

open class UriPartFilter(
    name: String,
    options: List<FilterOption>,
) : Filter.Select<String>(name, (listOf("Tất cả") + options.map(FilterOption::name)).toTypedArray()) {
    private val entries = listOf(null) + options.map(FilterOption::value)

    val selected: String?
        get() = entries[state]
}

class DoujinFilter(options: List<FilterOption>) : UriPartFilter("Doujin", options)

class AuthorFilter(options: List<FilterOption>) : UriPartFilter("Tác giả", options)

class ArtistFilter(options: List<FilterOption>) : UriPartFilter("Họa sĩ", options)

class TagFilter(options: List<FilterOption>) : UriPartFilter("Thể loại", options)

class GroupFilter(options: List<FilterOption>) : UriPartFilter("Nhóm dịch", options)

class CoupleFilter(options: List<FilterOption>) : UriPartFilter("Couple", options)

class SortFilter :
    UriPartFilter(
        "Sắp xếp",
        listOf(
            FilterOption("Mới cập nhật", "latest"),
            FilterOption("Lượt xem (Tất cả)", "views"),
            FilterOption("Lượt xem (Ngày)", "views_day"),
            FilterOption("Lượt xem (Tuần)", "views_week"),
            FilterOption("Lượt xem (Tháng)", "views_month"),
            FilterOption("Yêu thích (Tất cả)", "bookmarks"),
            FilterOption("Yêu thích (Ngày)", "bookmarks_day"),
            FilterOption("Yêu thích (Tuần)", "bookmarks_week"),
            FilterOption("Yêu thích (Tháng)", "bookmarks_month"),
        ),
    )
