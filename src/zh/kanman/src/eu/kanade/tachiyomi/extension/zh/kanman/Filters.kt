package eu.kanade.tachiyomi.extension.zh.kanman

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters() = FilterList(
    SortFilter(),
    Filter.Header("搜索时忽略分类"),
    CategoryFilter(),
)

class SortFilter : Filter.Select<String>("排序", SORTS.map { it.first }.toTypedArray()) {
    val value get() = SORTS[state].second
}

class CategoryFilter : Filter.Select<String>("分类", CATEGORIES.map { it.first }.toTypedArray()) {
    val value get() = CATEGORIES[state].second
}

private val SORTS = listOf(
    "人气" to "click",
    "更新" to "date",
    "收藏" to "shoucang",
)

private val CATEGORIES = listOf(
    "全部" to null,
    "热血" to "rexue",
    "冒险" to "maoxian",
    "搞笑" to "gaoxiao",
    "战争" to "zhanzhen",
    "神魔" to "shenmo",
    "悬疑" to "xuanyi",
    "社会" to "shehui",
    "恋爱" to "lianai",
    "吸血" to "xixue",
    "萝莉" to "luoli",
    "霸总" to "bazong",
    "玄幻" to "xuanhuan",
    "古风" to "gufeng",
    "历史" to "lishi",
    "漫改" to "mangai",
    "游戏" to "youxi",
    "穿越" to "chuanyue",
    "科幻" to "kehuan",
    "都市" to "dushi",
    "武侠" to "wuxia",
    "修真" to "xiuzhen",
    "生活" to "shenghuo",
    "动作" to "dongzuo",
    "精品" to "jingpin",
    "连载" to "lianzai",
    "完结" to "wanjie",
)
