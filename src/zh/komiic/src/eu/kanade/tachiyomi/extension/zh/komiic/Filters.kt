package eu.kanade.tachiyomi.extension.zh.komiic

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

var categories: List<ItemDto> = emptyList()

fun buildFilterList(): FilterList {
    val categoryFilter = if (categories.isNotEmpty()) {
        CategoryFilter()
    } else {
        Filter.Header("點擊“重設”載入類型")
    }
    return FilterList(
        Filter.Header("篩選條件（搜索關鍵字時無效）"),
        categoryFilter,
        SortFilter(),
        StatusFilter(),
        RatingFilter(),
    )
}

interface KomiicFilter {
    fun apply(variables: ListingVariables)
}

class Category(val id: String, name: String) : Filter.CheckBox(name)

class CategoryFilter :
    Filter.Group<Category>("類型（篩選同時包含全部所選標簽的漫畫）", categories.map { Category(it.id, it.name) }),
    KomiicFilter {
    override fun apply(variables: ListingVariables) {
        variables.categoryId = state.mapNotNull { if (it.state) it.id else null }
    }
}

class StatusFilter :
    Filter.Select<String>("狀態", arrayOf("全部", "連載", "完結")),
    KomiicFilter {
    override fun apply(variables: ListingVariables) {
        variables.pagination.status = arrayOf("", "ONGOING", "END")[state]
    }
}

class SortFilter :
    Filter.Select<String>("排序", arrayOf("更新", "本月觀看數（不能篩選類型）", "觀看數", "喜愛數")),
    KomiicFilter {
    override fun apply(variables: ListingVariables) {
        variables.pagination.orderBy = arrayOf(OrderBy.DATE_UPDATED, OrderBy.MONTH_VIEWS, OrderBy.VIEWS, OrderBy.FAVORITE_COUNT)[state]
    }
}

class RatingFilter :
    Filter.Select<String>("色氣程度", arrayOf("全部", "無", "1", "2", "3", "≥4", "5")),
    KomiicFilter {
    override fun apply(variables: ListingVariables) {
        variables.pagination.sexyLevel = arrayOf(null, 0, 1, 2, 3, 4, 5)[state]
    }
}
