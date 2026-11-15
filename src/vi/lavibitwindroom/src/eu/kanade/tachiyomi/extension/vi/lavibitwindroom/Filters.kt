package eu.kanade.tachiyomi.extension.vi.lavibitwindroom

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

class TagTriState(name: String) : Filter.TriState(name)

class TagFilter(tags: List<String>) :
    Filter.Group<TagTriState>(
        "Thể loại",
        tags.map { TagTriState(it) },
    ) {
    fun included(): List<String> = state
        .filter { it.isIncluded() }
        .map { it.name }

    fun excluded(): List<String> = state
        .filter { it.isExcluded() }
        .map { it.name }
}

class StatusFilter :
    Filter.Select<String>(
        "Trạng thái",
        arrayOf("Tất cả", "Đang ra", "Hoàn thành", "Tạm ngưng"),
    ) {
    fun selected(): String = values[state]
}

fun getFilters(tags: List<String>?): FilterList {
    val filters = mutableListOf<Filter<*>>()
    if (!tags.isNullOrEmpty()) filters += TagFilter(tags)
    filters += StatusFilter()
    return FilterList(filters)
}
