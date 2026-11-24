package eu.kanade.tachiyomi.extension.zh.manwame

import eu.kanade.tachiyomi.source.model.Filter

open class PathFilter(
    name: String,
    val key: String,
    private val options: List<Pair<String, String>>,
    state: Int = 0,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray(), state) {
    val value get() = options[state].second
}

class AreaFilter :
    PathFilter(
        "地区",
        "area",
        listOf("全部" to "", "国内" to "guonei", "日本" to "riben", "韩国" to "hanguo"),
    )

class ThemeFilter(themes: List<Pair<String, String>>) : PathFilter("题材", "theme", listOf("全部" to "") + themes)

class StateFilter :
    PathFilter(
        "进度",
        "state",
        listOf("全部" to "", "连载" to "lianzai", "完结" to "wanjie"),
    )

class OrderFilter(state: Int = 0) :
    PathFilter(
        "排序",
        "order",
        listOf(
            "热门人气" to "views",
            "更新时间" to "update",
            "今日排行" to "$RANKING_PREFIX/today",
            "本周排行" to "$RANKING_PREFIX/week",
            "本月排行" to "$RANKING_PREFIX/month",
            "收藏排行" to "$RANKING_PREFIX/collects",
        ),
        state,
    )

const val RANKING_PREFIX = "ranking/type"
