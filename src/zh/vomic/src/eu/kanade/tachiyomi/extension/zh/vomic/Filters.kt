package eu.kanade.tachiyomi.extension.zh.vomic

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

class SearchQuery(val title: String, val category: String)

fun parseSearchQuery(query: String, filters: FilterList): SearchQuery {
    for (filter in filters) {
        if (filter is SearchCategoryToggle) {
            if (filter.state) return SearchQuery("", query)
        } else if (filter is CategoryFilter) {
            return SearchQuery(query, filter.state.trim())
        }
    }
    return SearchQuery(query, "")
}

fun getFilterListInternal() = FilterList(SearchCategoryToggle(), CategoryFilter())

private class SearchCategoryToggle : Filter.CheckBox("将搜索词视为分类，勾选后下面的文本框无效")

private class CategoryFilter : Filter.Text("分类")
