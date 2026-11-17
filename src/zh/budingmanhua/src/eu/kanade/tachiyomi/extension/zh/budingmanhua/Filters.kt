package eu.kanade.tachiyomi.extension.zh.budingmanhua

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

open class SelectFilter(name: String, private val options: List<Pair<String, String?>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val selected get() = options[state].second
}

class CategoryFilter : SelectFilter("题材", listOf("全部" to null) + CATEGORIES.map { it to it })

class AreaFilter : SelectFilter("地区", AREAS)

class StatusFilter : SelectFilter("进度", STATUSES)

fun getFilters() = FilterList(
    Filter.Header("关键词搜索时不使用筛选"),
    CategoryFilter(),
    AreaFilter(),
    StatusFilter(),
)

private val CATEGORIES = listOf("恐怖", "科幻", "悬疑", "真人", "穿越", "战争", "后宫", "霸总", "搞笑", "生活", "恋爱", "校园", "修真", "玄幻", "热血")

private val AREAS = listOf(
    "全部" to null,
    "韩漫" to "3",
    "日漫" to "2",
    "国漫" to "1",
)

private val STATUSES = listOf(
    "全部" to null,
    "连载" to "2",
    "完结" to "1",
)
