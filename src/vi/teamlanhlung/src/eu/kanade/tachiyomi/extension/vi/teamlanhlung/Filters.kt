package eu.kanade.tachiyomi.extension.vi.teamlanhlung

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.serialization.Serializable

fun getFilters(data: FilterData?): FilterList {
    val filters = mutableListOf<Filter<*>>()
    data?.genres?.takeIf { it.isNotEmpty() }?.let { filters += GenreFilter(it) }
    data?.teams?.takeIf { it.isNotEmpty() }?.let { filters += TeamFilter(it) }
    data?.series?.takeIf { it.isNotEmpty() }?.let { filters += SeriesFilter(it) }
    data?.keywords?.takeIf { it.isNotEmpty() }?.let { filters += KeywordFilter(it) }
    return FilterList(filters)
}

@Serializable
class FilterData(
    val genres: List<FilterOption>,
    val teams: List<FilterOption>,
    val series: List<FilterOption>,
    val keywords: List<FilterOption>,
)

@Serializable
class FilterOption(
    val name: String,
    val path: String,
)

class GenreFilter(options: List<FilterOption>) : UriPartFilter("Thể loại", options)

class TeamFilter(options: List<FilterOption>) : UriPartFilter("Nhóm", options)

class SeriesFilter(options: List<FilterOption>) : UriPartFilter("Loạt Truyện", options)

class KeywordFilter(options: List<FilterOption>) : UriPartFilter("Từ khóa", options)

open class UriPartFilter(
    displayName: String,
    options: List<FilterOption>,
) : Filter.Select<String>(displayName, (listOf("Tất cả") + options.map { it.name }).toTypedArray()) {
    private val paths = listOf("") + options.map { it.path }

    fun toUriPart(): String = paths[state]
}
