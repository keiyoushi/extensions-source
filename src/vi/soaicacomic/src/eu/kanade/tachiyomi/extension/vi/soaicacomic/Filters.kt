package eu.kanade.tachiyomi.extension.vi.soaicacomic

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.serialization.Serializable

fun getFilters(data: FilterData?): FilterList = FilterList(
    Filter.Header("Bộ lọc sẽ bị bỏ qua khi tìm kiếm"),
    *listOfNotNull(
        data?.genres?.takeIf { it.isNotEmpty() }?.let(::GenreFilter),
        data?.teams?.takeIf { it.isNotEmpty() }?.let(::TeamFilter),
        data?.series?.takeIf { it.isNotEmpty() }?.let(::SeriesFilter),
        data?.keywords?.takeIf { it.isNotEmpty() }?.let(::KeywordFilter),
    ).toTypedArray(),
)

@Serializable
class FilterData(
    val genres: List<FilterOption>,
    val teams: List<FilterOption>,
    val series: List<FilterOption>,
    val keywords: List<FilterOption>,
)

@Serializable
class FilterOption(val name: String, val path: String)

open class UriPartFilter(
    displayName: String,
    private val options: List<FilterOption>,
) : Filter.Select<String>(displayName, arrayOf("Tất cả") + options.map { it.name }) {
    fun toUriPart(): String = options.getOrNull(state - 1)?.path.orEmpty()
}

class GenreFilter(options: List<FilterOption>) : UriPartFilter("Thể loại", options)

class TeamFilter(options: List<FilterOption>) : UriPartFilter("Nhóm", options)

class SeriesFilter(options: List<FilterOption>) : UriPartFilter("Loạt Truyện", options)

class KeywordFilter(options: List<FilterOption>) : UriPartFilter("Từ khóa", options)
