package eu.kanade.tachiyomi.extension.zh.zerobyw

import eu.kanade.tachiyomi.source.model.Filter

internal class CategoryFilter :
    UriSelectFilterPath(
        "category_id",
        "分类",
        arrayOf(
            Pair("", "全部"),
            Pair("1", "卖肉"),
            Pair("15", "战斗"),
            Pair("32", "日常"),
            Pair("6", "后宫"),
            Pair("13", "搞笑"),
            Pair("28", "日常"),
            Pair("31", "爱情"),
            Pair("22", "冒险"),
            Pair("23", "奇幻"),
            Pair("26", "战斗"),
            Pair("29", "体育"),
            Pair("34", "机战"),
            Pair("35", "职业"),
            Pair("36", "汉化组跟上，不再更新"),
        ),
    )

internal class StatusFilter :
    UriSelectFilterPath(
        "jindu",
        "进度",
        arrayOf(
            Pair("", "全部"),
            Pair("0", "连载中"),
            Pair("1", "已完结"),
        ),
    )

internal class AttributeFilter :
    UriSelectFilterPath(
        "shuxing",
        "性质",
        arrayOf(
            Pair("", "全部"),
            Pair("一半中文一半生肉", "一半中文一半生肉"),
            Pair("全生肉", "全生肉"),
            Pair("全中文", "全中文"),
        ),
    )

/**
 * Class that creates a select filter. Each entry in the dropdown has a name and a display name.
 * If an entry is selected it is appended as a query parameter onto the end of the URI.
 */
// vals: <name, display>
internal open class UriSelectFilterPath(
    val key: String,
    displayName: String,
    val vals: Array<Pair<String, String>>,
) : Filter.Select<String>(displayName, vals.map { it.second }.toTypedArray()) {
    fun toUri() = Pair(key, vals[state].first)
}
