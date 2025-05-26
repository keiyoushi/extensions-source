package eu.kanade.tachiyomi.extension.zh.yymanhua

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun buildFilterList() = FilterList(
    Filter.Header("过滤条件（搜索时无效）"),
    ThemeFilter(),
    StatusFilter(),
    SortFilter(),
)

class ThemeFilter : Filter.Select<String>("题材", arrayOf("全部", "热血", "恋爱", "校园", "冒险", "科幻", "生活", "悬疑", "魔法", "运动")) {
    override fun toString(): String {
        return arrayOf("0", "31", "26", "1", "2", "25", "11", "17", "15", "34")[state]
    }
}

class StatusFilter : Filter.Select<String>("状态", arrayOf("全部", "连载中", "完结")) {
    override fun toString(): String {
        return arrayOf("0", "1", "2")[state]
    }
}

class SortFilter : Filter.Select<String>("排序", arrayOf("人气", "更新时间")) {
    override fun toString(): String {
        return arrayOf("10", "2")[state]
    }
}
