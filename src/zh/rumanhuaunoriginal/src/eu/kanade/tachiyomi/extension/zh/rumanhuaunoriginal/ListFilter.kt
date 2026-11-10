package eu.kanade.tachiyomi.extension.zh.rumanhuaunoriginal

import eu.kanade.tachiyomi.source.model.Filter

class ListFilter :
    Filter.Select<String>(
        "列表（搜索文本时无效）",
        LISTS.map { it.first }.toTypedArray(),
    ) {
    val path get() = LISTS[state].second
}

// Only the full catalogue (漫画大全) has more than one page
private val LISTS = listOf(
    "排行榜" to "custom/top",
    "漫画大全" to "category",
    "日榜" to "custom/day",
    "周榜" to "custom/week",
    "月榜" to "custom/month",
    "最新更新" to "custom/update",
    "连载" to "custom/serialize",
    "完结" to "custom/end",
)
