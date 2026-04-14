package eu.kanade.tachiyomi.extension.vi.baotangtruyen

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

class SortFilter :
    Filter.Select<String>(
        "Sắp xếp theo",
        SORTS.map { it.label }.toTypedArray(),
    ) {
    fun toUriPart(): String = SORTS[state].value
}

fun getFilters(): FilterList = FilterList(
    Filter.Header("Lọc theo Sắp xếp"),
    SortFilter(),
)

private class Option(
    val label: String,
    val value: String,
)

private val SORTS = arrayOf(
    Option("Thời gian đăng", "created_at"),
    Option("Lượt theo dõi", "likes"),
    Option("Lượt view", "views"),
)
