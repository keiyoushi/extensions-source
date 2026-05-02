package eu.kanade.tachiyomi.extension.zh.tencentcomics

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

class PopularityFilter :
    UriPartFilter(
        "热门人气/更新时间",
        arrayOf(
            Pair("热门人气", "hot/"),
            Pair("更新时间", "time/"),
        ),
    )

class VipFilter :
    UriPartFilter(
        "属性",
        arrayOf(
            Pair("全部", ""),
            Pair("付费", "vip/2/"),
            Pair("免费", "vip/1/"),
        ),
    )

class StatusFilter :
    UriPartFilter(
        "进度",
        arrayOf(
            Pair("全部", ""),
            Pair("连载中", "finish/1/"),
            Pair("已完结", "finish/2/"),
        ),
    )

class GenreFilter :
    UriPartFilter(
        "标签",
        arrayOf(
            Pair("全部", ""),
            Pair("恋爱", "105"),
            Pair("玄幻", "101"),
            Pair("异能", "103"),
            Pair("恐怖", "110"),
            Pair("剧情", "106"),
            Pair("科幻", "108"),
            Pair("悬疑", "112"),
            Pair("奇幻", "102"),
            Pair("冒险", "104"),
            Pair("犯罪", "111"),
            Pair("动作", "109"),
            Pair("日常", "113"),
            Pair("竞技", "114"),
            Pair("武侠", "115"),
            Pair("历史", "116"),
            Pair("战争", "117"),
        ),
    )
