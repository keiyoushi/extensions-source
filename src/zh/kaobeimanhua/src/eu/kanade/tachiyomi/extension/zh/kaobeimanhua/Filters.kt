package eu.kanade.tachiyomi.extension.zh.kaobeimanhua

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter :
    Filter.Sort(
        "排序",
        SORTS.map { it.first }.toTypedArray(),
        Selection(0, false),
    ) {
    val ordering get() = (if (state!!.ascending) "" else "-") + SORTS[state!!.index].second
}

class ThemeFilter : Filter.Select<String>("題材", THEMES.map { it.first }.toTypedArray()) {
    val selected get() = THEMES[state].second
}

private val SORTS = listOf(
    "更新時間" to "datetime_updated",
    "熱度" to "popular",
)

private val THEMES = listOf(
    "全部" to null,
    "愛情" to "aiqing",
    "歡樂向" to "huanlexiang",
    "冒險" to "maoxian",
    "奇幻" to "qihuan",
    "百合" to "baihe",
    "校园" to "xiaoyuan",
    "科幻" to "kehuan",
    "東方" to "dongfang",
    "耽美" to "danmei",
    "格鬥" to "gedou",
    "TL" to "teenslove",
    "生活" to "shenghuo",
    "轻小说" to "qingxiaoshuo",
    "其他" to "qita",
    "悬疑" to "xuanyi",
    "萌系" to "mengxi",
    "神鬼" to "shengui",
    "职场" to "zhichang",
    "治愈" to "zhiyu",
    "节操" to "jiecao",
)
