package eu.kanade.tachiyomi.extension.zh.dmzj

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilterListInternal(isMultiGenre: Boolean) = FilterList(
    RankingGroup(),
    Filter.Separator(),
    Filter.Header("分类筛选（查看排行榜、搜索文本时无效）"),
    if (isMultiGenre) GenreGroup() else GenreSelectFilter(),
    StatusFilter(),
    ReaderFilter(),
    RegionFilter(),
    SortFilter(),
)

// region Ranking filters

class RankingGroup : Filter.Group<Filter<*>>(
    "排行榜（搜索文本时无效）",
    listOf<Filter<*>>(
        EnabledFilter(),
        TimeFilter(),
        SortFilter(),
        GenreFilter(),
    ),
) {
    val isEnabled get() = (state[0] as EnabledFilter).state

    fun parse() = state.filterIsInstance<QueryFilter>().joinToString("&") { it.uriPart }

    private class EnabledFilter : CheckBox("查看排行榜")

    private class TimeFilter : QueryFilter(
        "榜单",
        "by_time",
        arrayOf(
            Pair("日排行", "0"),
            Pair("周排行", "1"),
            Pair("月排行", "2"),
            Pair("总排行", "3"),
        ),
    )

    private class SortFilter : QueryFilter(
        "排序",
        "rank_type",
        arrayOf(
            Pair("人气", "0"),
            Pair("吐槽", "1"),
            Pair("订阅", "2"),
        ),
    )

    private class GenreFilter : QueryFilter("题材(慎用/易出错)", "tag_id", genres)

    private open class QueryFilter(
        name: String,
        private val query: String,
        values: Array<Pair<String, String>>,
    ) : SelectFilter(name, values) {
        override val uriPart get() = query + '=' + super.uriPart
    }
}

// endregion

// region Normal filters

fun parseFilters(filters: FilterList): String {
    val tags = filters.filterIsInstance<TagFilter>().mapNotNull {
        it.uriPart.takeUnless(String::isEmpty)
    }.joinToString("-").ifEmpty { "0" }
    val sort = filters.filterIsInstance<SortFilter>().firstOrNull()?.uriPart ?: "0"
    return "$tags/$sort"
}

private interface TagFilter : UriPartFilter

private class GenreSelectFilter : TagFilter, SelectFilter("题材", genres)

private class GenreGroup : TagFilter, Filter.Group<GenreFilter>(
    "题材（作品需包含勾选的所有项目）",
    genres.drop(1).map { GenreFilter(it.first, it.second) },
) {
    override val uriPart get() = state.filter { it.state }.joinToString("-") { it.value }
}

private class GenreFilter(name: String, val value: String) : Filter.CheckBox(name)

private class StatusFilter : TagFilter, SelectFilter(
    "状态",
    arrayOf(
        Pair("全部", ""),
        Pair("连载中", "2309"),
        Pair("已完结", "2310"),
    ),
)

private class ReaderFilter : TagFilter, SelectFilter(
    "受众",
    arrayOf(
        Pair("全部", ""),
        Pair("少年漫画", "3262"),
        Pair("少女漫画", "3263"),
        Pair("青年漫画", "3264"),
        Pair("女青漫画", "13626"),
    ),
)

private class RegionFilter : TagFilter, SelectFilter(
    "地域",
    arrayOf(
        Pair("全部", ""),
        Pair("日本", "2304"),
        Pair("韩国", "2305"),
        Pair("欧美", "2306"),
        Pair("港台", "2307"),
        Pair("内地", "2308"),
        Pair("其他", "8453"),
    ),
)

private class SortFilter : SelectFilter(
    "排序",
    arrayOf(
        Pair("人气", "0"),
        Pair("更新", "1"),
    ),
)

// endregion

private val genres
    get() = arrayOf(
        Pair("全部", ""),
        Pair("冒险", "4"),
        Pair("欢乐向", "5"),
        Pair("格斗", "6"),
        Pair("科幻", "7"),
        Pair("爱情", "8"),
        Pair("侦探", "9"),
        Pair("竞技", "10"),
        Pair("魔法", "11"),
        Pair("神鬼", "12"),
        Pair("校园", "13"),
        Pair("惊悚", "14"),
        Pair("其他", "16"),
        Pair("四格", "17"),
        Pair("生活", "3242"),
        Pair("ゆり", "3243"),
        Pair("秀吉", "3244"),
        Pair("悬疑", "3245"),
        Pair("纯爱", "3246"),
        Pair("热血", "3248"),
        Pair("泛爱", "3249"),
        Pair("历史", "3250"),
        Pair("战争", "3251"),
        Pair("萌系", "3252"),
        Pair("宅系", "3253"),
        Pair("治愈", "3254"),
        Pair("励志", "3255"),
        Pair("武侠", "3324"),
        Pair("机战", "3325"),
        Pair("音乐舞蹈", "3326"),
        Pair("美食", "3327"),
        Pair("职场", "3328"),
        Pair("西方魔幻", "3365"),
        Pair("高清单行", "4459"),
        Pair("TS", "4518"),
        Pair("东方", "5077"),
        Pair("魔幻", "5806"),
        Pair("奇幻", "5848"),
        Pair("节操", "6219"),
        Pair("轻小说", "6316"),
        Pair("颜艺", "6437"),
        Pair("搞笑", "7568"),
        Pair("仙侠", "7900"),
        Pair("舰娘", "13627"),
        Pair("动画", "17192"),
        Pair("AA", "18522"),
        Pair("福瑞", "23323"),
        Pair("生存", "23388"),
        Pair("2021大赛", "23399"),
        Pair("未来漫画家", "25011"),
    )

interface UriPartFilter {
    val uriPart: String
}

private open class SelectFilter(
    name: String,
    values: Array<Pair<String, String>>,
) : UriPartFilter, Filter.Select<String>(
    name = name,
    values = Array(values.size) { values[it].first },
) {
    private val uriParts = Array(values.size) { values[it].second }
    override val uriPart get() = uriParts[state]
}
