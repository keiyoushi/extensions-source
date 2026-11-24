package eu.kanade.tachiyomi.extension.zh.woaimanhua

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

open class SelectFilter(name: String, private val options: List<Pair<String, String>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val selected get() = options[state].second
}

class TagFilter : SelectFilter("题材", TAGS.map { it to it })

class AreaFilter : SelectFilter("地区", AREAS)

class StatusFilter : SelectFilter("进度", STATUSES)

fun getFilters() = FilterList(
    Filter.Header("关键词搜索时不使用筛选"),
    TagFilter(),
    AreaFilter(),
    StatusFilter(),
)

const val LATEST = "last"

private val TAGS = listOf("全部", "恋爱", "古风", "校园", "奇幻", "大女主", "治愈", "穿越", "励志", "爆笑", "萌系", "玄幻", "日常", "都市", "彩虹", "灵异", "悬疑", "少年")

private val AREAS = listOf(
    "全部" to "-1",
    "国产漫画" to "1",
    "日本漫画" to "2",
    "韩国漫画" to "3",
    "欧美漫画" to "5",
    "其他漫画" to "7",
    "日韩小说" to "8",
)

private val STATUSES = listOf(
    "全部" to "-1",
    "最新" to LATEST,
    "连载中" to "0",
    "已完结" to "1",
)
