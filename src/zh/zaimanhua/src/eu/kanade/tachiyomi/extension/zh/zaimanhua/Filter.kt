package eu.kanade.tachiyomi.extension.zh.zaimanhua

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

class SearchByIdFilter : Filter.CheckBox("启用ID跳转（搜索纯数字时）")

open class QueryFilter(name: String, private val key: String, private val params: Array<Pair<String, String>>) :
    Filter.Select<String>(name, params.map { it.first }.toTypedArray()) {
    fun addQuery(builder: HttpUrl.Builder) {
        val param = params[state].second
        if (param.isNotEmpty()) {
            builder.addQueryParameter(key, param)
        }
    }
}
class RankingGroup : Filter.Group<Filter<*>>(
    "排行榜（搜索时无效）",
    listOf<Filter<*>>(
        TimeFilter(),
        SortFilter(),
    ),
) {
    private class SortFilter : QueryFilter(
        "排序",
        "rank_type",
        arrayOf(
            Pair("人气", "0"),
            Pair("吐槽", "1"),
            Pair("订阅", "2"),
        ),
    )
}

class TimeFilter : QueryFilter(
    "榜单",
    "by_time",
    arrayOf(
        Pair("不查看榜单", ""),
        Pair("日排行", "0"),
        Pair("周排行", "1"),
        Pair("月排行", "2"),
        Pair("总排行", "3"),
    ),
)

class GenreGroup : Filter.Group<Filter<*>>(
    "筛选出满足以下所有条件的漫画",
    listOf<Filter<*>>(
        SortTypeFilter(),
        StatusFilter(),
        CateFilter(),
        ZoneFilter(),
        ThemeFilter(),
    ),
) {

    private class SortTypeFilter : QueryFilter(
        "排序",
        "sortType",
        arrayOf(
            Pair("更新排序", "1"),
            Pair("人气排序", "2"),
        ),
    )

    private class StatusFilter : QueryFilter(
        "进度",
        "status",
        arrayOf(
            Pair("全部", "0"),
            Pair("连载中", "2309"),
            Pair("已完结", "2310"),
            Pair("短篇", "29205"),
        ),
    )

    private class CateFilter : QueryFilter(
        "读者群",
        "cate",
        arrayOf(
            Pair("全部", "0"),
            Pair("少年漫画", "3262"),
            Pair("少女漫画", "3263"),
            Pair("青年漫画", "3264"),
            Pair("女青漫画", "13626"),
        ),
    )

    private class ZoneFilter : QueryFilter(
        "地区",
        "zone",
        arrayOf(
            Pair("全部", "0"),
            Pair("日本", "2304"),
            Pair("韩国", "2305"),
            Pair("欧美", "2306"),
            Pair("港台", "2307"),
            Pair("内地", "2308"),
            Pair("其他", "8435"),
        ),
    )

    private class ThemeFilter : QueryFilter(
        "题材",
        "theme",
        arrayOf(
            Pair("全部", "0"),
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
            Pair("亲情", "3242"),
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
            Pair("日常", "30788"),
            Pair("画集", "31137"),
        ),
    )
}
