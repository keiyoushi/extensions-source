package eu.kanade.tachiyomi.extension.zh.baozimanhua

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(groups: List<FilterGroup>?): FilterList {
    val filters = groups.orEmpty().mapNotNull { group ->
        FILTER_TITLES[group.param]?.let { UriPartFilter(it, group.param, group.options) }
    }
    if (filters.isEmpty()) return FilterList()
    return FilterList(listOf(Filter.Header("注意：不影響按標題搜索")) + filters)
}

class UriPartFilter(name: String, val param: String, private val options: List<FilterOption>) : Filter.Select<String>(name, options.map { it.name }.toTypedArray()) {
    val value get() = options[state].value
}

private val FILTER_TITLES = mapOf(
    "region" to "地区",
    "state" to "进度",
    "type" to "标签",
    "filter" to "标题开头",
)
