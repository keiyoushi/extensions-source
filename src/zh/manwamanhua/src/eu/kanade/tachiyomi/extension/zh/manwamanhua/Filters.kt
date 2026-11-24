package eu.kanade.tachiyomi.extension.zh.manwamanhua

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

open class SelectFilter(name: String, private val options: List<Pair<String, Int>>, state: Int = 0) : Filter.Select<String>(name, options.map { it.first }.toTypedArray(), state) {
    val value get() = options[state].second
}

class SortFilter(state: Int) : SelectFilter("排序", listOf("更新" to 0, "新作" to 1, "热门" to 3), state) {
    companion object {
        val LATEST get() = FilterList(SortFilter(0))
        val POPULAR get() = FilterList(SortFilter(2))
    }
}

class StatusFilter : SelectFilter("状态", listOf("全部" to -1, "连载中" to 0, "已完结" to 1))

class AreaFilter : SelectFilter("地区", listOf("全部" to 0, "韩国" to 2, "日漫" to 3, "国漫" to 4, "台漫" to 5, "其他" to 6, "未分类" to 1))

class LevelFilter : SelectFilter("等级", listOf("全部" to 0, "普通" to 1, "VIP無码" to 2))

class DayFilter : SelectFilter("更新日", listOf("全部" to 0, "周一" to 1, "周二" to 2, "周三" to 3, "周四" to 4, "周五" to 5, "周六" to 6, "周日" to 7))

class TagOption(name: String, val slug: String) : Filter.CheckBox(name)

class TagFilter(tags: List<Pair<String, String>>) : Filter.Group<TagOption>("标签", tags.map { TagOption(it.first, it.second) }) {
    val slugs get() = state.filter { it.state }.map { it.slug }
}

class SearchFieldFilter : Filter.Select<String>("搜索范围", SEARCH_FIELDS.map { it.first }.toTypedArray()) {
    val field get() = SEARCH_FIELDS[state].second

    companion object {
        private val SEARCH_FIELDS = listOf("标题" to "keyword", "作者" to "author", "标签" to "tags")
    }
}
