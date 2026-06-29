package eu.kanade.tachiyomi.extension.zh.kuaikanmanhua

import eu.kanade.tachiyomi.source.model.Filter

internal class GenreFilter :
    UriPartFilter(
        "题材",
        arrayOf(
            Pair("全部", "0"),
            Pair("恋爱", "20"),
            Pair("古风", "46"),
            Pair("穿越", "80"),
            Pair("大女主", "77"),
            Pair("青春", "47"),
            Pair("非人类", "92"),
            Pair("奇幻", "22"),
            Pair("都市", "48"),
            Pair("总裁", "52"),
            Pair("强剧情", "82"),
            Pair("玄幻", "63"),
            Pair("系统", "86"),
            Pair("悬疑", "65"),
            Pair("末世", "91"),
            Pair("热血", "67"),
            Pair("萌系", "62"),
            Pair("搞笑", "71"),
            Pair("重生", "89"),
            Pair("异能", "68"),
            Pair("冒险", "93"),
            Pair("武侠", "85"),
            Pair("竞技", "72"),
            Pair("正能量", "54"),

            // NOTE: Old categories that seem to work but are not shown on the website anymore. Keeping them here just in case.
            // Pair("治愈", "27"),
            // Pair("完结", "40"),
            // Pair("唯美", "58"),
            // Pair("日漫", "57"),
            // Pair("韩漫", "60"),
            // Pair("灵异", "32"),
            // Pair("爆笑", "24"),
            // Pair("日常", "19"),
            // Pair("投稿", "76"),
        ),
    )

internal class RegionFilter :
    UriPartFilter(
        "区域",
        arrayOf(
            Pair("全部", "1"),
            Pair("国漫", "2"),
            Pair("韩漫", "3"),
            Pair("日漫", "4"),
        ),
    )

internal class PaysFilter :
    UriPartFilter(
        "属性",
        arrayOf(
            Pair("全部", "0"),
            Pair("免费", "1"),
            Pair("付费", "2"),
            Pair("VIP抢先看", "3"),
        ),
    )

internal class StatusFilter :
    UriPartFilter(
        "进度",
        arrayOf(
            Pair("全部", "0"),
            Pair("连载中", "1"),
            Pair("已完结", "2"),
        ),
    )

internal class SortFilter :
    UriPartFilter(
        "排序",
        arrayOf(
            Pair("推荐", "1"),
            Pair("最火热", "2"),
            Pair("新上架", "3"),
        ),
    )

internal open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}
