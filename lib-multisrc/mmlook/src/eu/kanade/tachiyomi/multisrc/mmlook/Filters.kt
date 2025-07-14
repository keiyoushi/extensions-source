package eu.kanade.tachiyomi.multisrc.mmlook

import eu.kanade.tachiyomi.source.model.Filter

class Option(val name: String, val value: String)

open class SelectFilter(name: String, val options: Array<Option>) :
    Filter.Select<String>(name, Array(options.size) { options[it].name })

class RankingFilter : SelectFilter(
    "排行榜",
    arrayOf(
        Option("不查看", ""),
        Option("精品榜", "1"),
        Option("人气榜", "2"),
        Option("推荐榜", "3"),
        Option("黑马榜", "4"),
        Option("最近更新", "5"),
        Option("新漫画", "6"),
    ),
)

class CategoryFilter : SelectFilter(
    "分类",
    arrayOf(
        Option("全部", ""),
        Option("冒险", "1"),
        Option("热血", "2"),
        Option("都市", "3"),
        Option("玄幻", "4"),
        Option("悬疑", "5"),
        Option("耽美", "6"),
        Option("恋爱", "7"),
        Option("生活", "8"),
        Option("搞笑", "9"),
        Option("穿越", "10"),
        Option("修真", "11"),
        Option("后宫", "12"),
        Option("女主", "13"),
        Option("古风", "14"),
        Option("连载", "15"),
        Option("完结", "16"),
    ),
)
