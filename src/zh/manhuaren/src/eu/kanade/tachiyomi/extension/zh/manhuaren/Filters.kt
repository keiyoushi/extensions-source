package eu.kanade.tachiyomi.extension.zh.manhuaren

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(): FilterList = FilterList(
    SortFilter(
        "状态",
        arrayOf(
            Pair("热门", "0"),
            Pair("更新", "1"),
            Pair("新作", "2"),
            Pair("完结", "3"),
        ),
    ),
    CategoryFilter(
        "分类",
        arrayOf(
            Category("全部", "0", "0"),
            Category("热血", "0", "31"),
            Category("恋爱", "0", "26"),
            Category("校园", "0", "1"),
            Category("百合", "0", "3"),
            Category("耽美", "0", "27"),
            Category("伪娘", "0", "5"),
            Category("冒险", "0", "2"),
            Category("职场", "0", "6"),
            Category("后宫", "0", "8"),
            Category("治愈", "0", "9"),
            Category("科幻", "0", "25"),
            Category("励志", "0", "10"),
            Category("生活", "0", "11"),
            Category("战争", "0", "12"),
            Category("悬疑", "0", "17"),
            Category("推理", "0", "33"),
            Category("搞笑", "0", "37"),
            Category("奇幻", "0", "14"),
            Category("魔法", "0", "15"),
            Category("恐怖", "0", "29"),
            Category("神鬼", "0", "20"),
            Category("萌系", "0", "21"),
            Category("历史", "0", "4"),
            Category("美食", "0", "7"),
            Category("同人", "0", "30"),
            Category("运动", "0", "34"),
            Category("绅士", "0", "36"),
            Category("机甲", "0", "40"),
            Category("限制级", "0", "61"),
            Category("少年向", "1", "1"),
            Category("少女向", "1", "2"),
            Category("青年向", "1", "3"),
            Category("港台", "2", "35"),
            Category("日韩", "2", "36"),
            Category("大陆", "2", "37"),
            Category("欧美", "2", "52"),
        ),
    ),
)

internal data class Category(val name: String, val type: String, val id: String)

internal class SortFilter(
    name: String,
    private val vals: Array<Pair<String, String>>,
    state: Int = 0,
) : Filter.Select<String>(
    name,
    vals.map { it.first }.toTypedArray(),
    state,
) {
    fun getId() = vals[state].second
}

internal class CategoryFilter(
    name: String,
    private val vals: Array<Category>,
    state: Int = 0,
) : Filter.Select<String>(
    name,
    vals.map { it.name }.toTypedArray(),
    state,
) {
    fun getId() = vals[state].id
    fun getType() = vals[state].type
}
