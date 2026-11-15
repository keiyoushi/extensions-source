package eu.kanade.tachiyomi.extension.zh.ikmmh

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

/**
 * 站点分类浏览 URL：/booklists/{area}/{tag}/{status}/{page}.html（站点按 1 起始分页）
 * - area: 9 全部 / 1 日漫 / 4 国漫 / 5 韩漫 / 6 未分类
 * - tag: 站点题材名（40 项与站点导航逐字一致，注意「後宫」用繁体「後」）
 * - status: 3 全部 / 1 连载中 / 4 已完结
 */
internal fun buildFilterList(): FilterList = FilterList(
    Filter.Header("分类/题材/状态筛选（应用于分类浏览）"),
    AreaFilter(),
    TagFilter(),
    StatusFilter(),
)

internal open class SelectFilter(name: String, private val options: List<Pair<String, String>>) : Filter.Select<String>(name, options.map { it.second }.toTypedArray()) {
    fun selected(): Pair<String, String> = options[state]
}

internal class AreaFilter :
    SelectFilter(
        "地区",
        listOf(
            "9" to "全部",
            "1" to "日漫",
            "4" to "国漫",
            "5" to "韩漫",
            "6" to "未分类",
        ),
    )

internal class TagFilter : SelectFilter("题材", TAG_OPTIONS)

internal class StatusFilter : Filter.TriState("状态") {
    override fun toString() = when (state) {
        Filter.TriState.STATE_INCLUDE -> "1" // 连载中
        Filter.TriState.STATE_EXCLUDE -> "4" // 已完结
        else -> "3" // 全部
    }
}

private val TAG_OPTIONS: List<Pair<String, String>> = listOf(
    "全部", "长条", "大女主", "百合", "耽美", "纯爱", "後宫", "韩漫", "奇幻", "轻小说",
    "生活", "悬疑", "格斗", "搞笑", "伪娘", "竞技", "职场", "萌系", "冒险", "治愈",
    "都市", "霸总", "神鬼", "侦探", "爱情", "古风", "欢乐向", "科幻", "穿越", "性转换",
    "校园", "美食", "剧情", "热血", "节操", "励志", "异世界", "历史", "战争", "恐怖",
).map { it to it }
