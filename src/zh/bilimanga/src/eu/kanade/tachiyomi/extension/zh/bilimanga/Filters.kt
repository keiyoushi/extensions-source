package eu.kanade.tachiyomi.extension.zh.bilimanga

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun buildFilterList() = FilterList(
    Filter.Header("篩選條件（搜尋時無效）"),
    RankFilter(),
)

class RankFilter : Filter.Select<String>(
    "排行榜",
    arrayOf(
        "月點擊榜",
        "周點擊榜",
        "月推薦榜",
        "周推薦榜",
        "月鮮花榜",
        "周鮮花榜",
        "月雞蛋榜",
        "周雞蛋榜",
        "最新入庫",
        "收藏榜",
        "新書榜",
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
