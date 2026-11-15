package eu.kanade.tachiyomi.extension.vi.panomic

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(data: FilterData?): FilterList = FilterList(
    buildList {
        add(Filter.Header("Bộ lọc sẽ bị bỏ qua khi tìm kiếm"))
        data ?: return@buildList
        if (data.genres.isNotEmpty()) add(GenreFilter(data.genres.withAllOption()))
        if (data.groups.isNotEmpty()) add(GroupFilter(data.groups.withAllOption()))
        if (data.series.isNotEmpty()) add(SeriesTypeFilter(data.series.withAllOption()))
        if (data.keywords.isNotEmpty()) add(KeywordFilter(data.keywords.withAllOption()))
    },
)

class GenreFilter(options: List<FilterOption>) : UriPartFilter("Thể loại", options)

class GroupFilter(options: List<FilterOption>) : UriPartFilter("Nhóm", options)

class SeriesTypeFilter(options: List<FilterOption>) : UriPartFilter("Loạt truyện", options)

class KeywordFilter(options: List<FilterOption>) : UriPartFilter("Từ khóa", options)

open class UriPartFilter(
    displayName: String,
    private val options: List<FilterOption>,
) : Filter.Select<String>(displayName, options.map { it.name }.toTypedArray()) {
    fun toUriPart(): String = options[state].uri
}

private fun List<FilterOption>.withAllOption() = listOf(FilterOption("Tất cả", "")) + this
