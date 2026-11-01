package eu.kanade.tachiyomi.extension.zh.ikanmh

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

internal class TagFilter : Filter.Select<String>("分类", TAGS) {
    fun toUriPart() = TAGS[state]
}

internal class AreaFilter : Filter.Select<String>("地区", AREAS.map { it.first }.toTypedArray()) {
    fun toUriPart() = AREAS[state].second
}

internal class StatusFilter : Filter.Select<String>("进度", ENDS.map { it.first }.toTypedArray()) {
    fun toUriPart() = ENDS[state].second
}

// /tag 页面当前展示的分类集合；显示名即 /booklist/?tag= 的取值
private val TAGS = arrayOf(
    "全部",
    "青春",
    "性感",
    "长腿",
    "多人",
    "御姐",
    "巨乳",
    "新婚",
    "媳妇",
    "暧昧",
    "清纯",
    "调教",
    "少妇",
    "风骚",
    "同居",
    "淫乱",
    "好友",
    "女神",
    "诱惑",
    "偷情",
    "出轨",
    "正妹",
    "家教",
)

// 站点 /booklist/?area=&end= 的 data-val 实测
private val AREAS = arrayOf(
    "全部" to "-1",
    "韩国" to "1",
    "日本" to "2",
    "台湾" to "3",
)

private val ENDS = arrayOf(
    "全部" to "-1",
    "连载中" to "0",
    "已完结" to "1",
)

internal fun buildFilterList() = FilterList(
    TagFilter(),
    AreaFilter(),
    StatusFilter(),
)
