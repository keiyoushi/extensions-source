package eu.kanade.tachiyomi.extension.vi.medamtruyen

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.serialization.Serializable

fun getFilters(data: FilterData?): FilterList = FilterList(
    buildList {
        add(Filter.Header("Bộ lọc sẽ bị bỏ qua khi tìm kiếm"))
        data?.genres?.takeIf { it.isNotEmpty() }?.let { add(GenreFilter(it)) }
        data?.groups?.takeIf { it.isNotEmpty() }?.let { add(GroupFilter(it)) }
        data?.authors?.takeIf { it.isNotEmpty() }?.let { add(AuthorFilter(it)) }
    },
)

@Serializable
class FilterData(
    val genres: List<FilterOption>,
    val groups: List<FilterOption>,
    val authors: List<FilterOption>,
)

@Serializable
class FilterOption(
    val name: String,
    val path: String,
)

class GenreFilter(options: List<FilterOption>) : UriPartFilter("Thể loại", options)

class GroupFilter(options: List<FilterOption>) : UriPartFilter("Nhóm", options)

class AuthorFilter(options: List<FilterOption>) : UriPartFilter("Tác giả", options)

open class UriPartFilter(
    displayName: String,
    options: List<FilterOption>,
) : Filter.Select<String>(displayName, (listOf("Tất cả") + options.map { it.name }).toTypedArray()) {
    private val paths = listOf<String?>(null) + options.map { it.path }

    fun toUriPart(): String? = paths[state]
}
