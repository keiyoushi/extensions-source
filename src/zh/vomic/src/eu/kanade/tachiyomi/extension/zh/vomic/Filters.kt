package eu.kanade.tachiyomi.extension.zh.vomic

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(
    displayName: String,
    private val vals: Array<Pair<String, String>>,
    private val default: Int? = null,
) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[default ?: state].second
}

internal class SortFilter(default: Int? = null) :
    UriPartFilter(
        "排序",
        arrayOf(
            "默认" to "",
            "最热" to "hot_value",
            "最新" to "process_at",
        ),
        default,
    )

internal class StatusFilter :
    UriPartFilter(
        "状态",
        arrayOf(
            "全部" to "",
            "连载中" to "连载中",
            "已完结" to "已完结",
        ),
    )

internal class CreativityFilter :
    UriPartFilter(
        "来源",
        arrayOf(
            "全部" to "",
            "原创" to "原创",
            "汉化" to "汉化",
            "转载" to "转载",
        ),
    )

internal class RegionFilter :
    UriPartFilter(
        "地区",
        arrayOf(
            "全部" to "",
            "大陆" to "大陆",
            "日本" to "日本",
            "韩国" to "韩国",
            "港台" to "港台",
            "欧美" to "欧美",
            "其他" to "其他",
        ),
    )

internal class DisplayTypeFilter :
    UriPartFilter(
        "类型",
        arrayOf(
            "全部" to "",
            "条漫" to "条漫",
            "页漫" to "页漫",
            "其他" to "其他",
        ),
    )

internal class CategoryFilter(categories: Map<String, Int>) :
    UriPartFilter(
        "分类",
        arrayOf(Pair("全部", "0")) +
            categories.map { (name, id) -> Pair(name, id.toString()) },
    )
