package eu.kanade.tachiyomi.extension.vi.lxmangaorg

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

internal fun getFilters(data: FilterData): FilterList = FilterList(
    buildList {
        add(Filter.Header("Bộ lọc bị bỏ qua khi nhập từ khóa tìm kiếm"))
        if (data.classifications.isNotEmpty()) add(ClassificationFilter(data.classifications))
        if (data.genres.isNotEmpty()) add(GenreFilter(data.genres))
        if (data.doujinshi.isNotEmpty()) add(DoujinshiFilter(data.doujinshi))
        if (data.authors.isNotEmpty()) add(AuthorFilter(data.authors))
    },
)

internal open class UriPartFilter(name: String, options: List<FilterOption>) : Filter.Select<FilterOption>(name, listOf(FilterOption("Tất cả", ""), *options.toTypedArray()).toTypedArray()) {

    fun selectedPath(): String? = values[state].path.ifBlank { null }
}

internal class ClassificationFilter(options: List<FilterOption>) : UriPartFilter("Phân loại", options)
internal class GenreFilter(options: List<FilterOption>) : UriPartFilter("Thể loại", options)
internal class DoujinshiFilter(options: List<FilterOption>) : UriPartFilter("Doujinshi", options)
internal class AuthorFilter(options: List<FilterOption>) : UriPartFilter("Tác giả", options)
