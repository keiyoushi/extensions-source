package eu.kanade.tachiyomi.extension.zh.nnhanman

import eu.kanade.tachiyomi.source.model.Filter

private val GENRES = arrayOf(
    "全部" to "all",
    "正妹" to "正妹",
    "恋爱" to "恋爱",
    "出版漫画" to "出版漫画",
    "肉慾" to "肉慾",
    "浪漫" to "浪漫",
    "大尺度" to "大尺度",
    "巨乳" to "巨乳",
    "有夫之婦" to "有夫之婦",
    "女大生" to "女大生",
    "狗血劇" to "狗血劇",
    "同居" to "同居",
    "好友" to "好友",
    "調教" to "調教",
    "动作" to "动作",
    "後宮" to "後宮",
    "不倫" to "不倫",
    "3D" to "3D",
    "校園" to "校園",
    "耽美" to "耽美",
    "日漫" to "日漫",
)

internal class GenreFilter : Filter.Select<String>("分类", GENRES.map { it.first }.toTypedArray()) {
    fun toUriPart() = GENRES[state].second
}

internal class OrderFilter : Filter.Select<String>("排序", arrayOf("按时间", "按热度")) {
    fun toUriPart() = arrayOf("time", "hits")[state]
}

internal class StatusFilter : Filter.Select<String>("状态", arrayOf("全部", "已完结", "连载中")) {
    fun toUriPart() = arrayOf("all", "completed", "serialized")[state]
}
