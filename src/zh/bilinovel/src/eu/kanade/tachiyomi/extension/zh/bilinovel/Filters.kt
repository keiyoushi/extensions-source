package eu.kanade.tachiyomi.extension.zh.bilinovel

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun buildFilterList() = FilterList(
    Filter.Header("筛选条件（搜索时无效）"),
    RankFilter(),
)

class RankFilter : Filter.Select<String>(
    "排行榜",
    arrayOf(
        "月点击榜",
        "周点击榜",
        "月推荐榜",
        "周推荐榜",
        "月鲜花榜",
        "周鲜花榜",
        "月鸡蛋榜",
        "周鸡蛋榜",
        "最新入库",
        "收藏榜",
        "新书榜",
    ),
) {
    override fun toString(): String {
        return arrayOf(
            "monthvisit",
            "weekvisit",
            "monthvote",
            "weekvote",
            "monthflower",
            "weekflower",
            "monthegg",
            "weekegg",
            "postdate",
            "goodnum",
            "newhot",
        )[state]
    }
}
