package eu.kanade.tachiyomi.extension.zh.yidan

import eu.kanade.tachiyomi.source.model.Filter

open class PairFilter(name: String, private val pairs: List<Pair<String, Int>>) :
    Filter.Select<String>(name, pairs.map { it.first }.toTypedArray()) {
    val selected: Int
        get() = pairs[state].second
}

class CategoryFilter : PairFilter(
    "分类",
    listOf(
        "青梅竹马" to 18,
        "办公室" to 19,
        "娱乐圈" to 20,
        "高H" to 21,
        "韩国版单" to 22,
        "NP/SM" to 23,
        "校园" to 24,
        "财阀" to 25,
        "重生/重逢" to 26,
        "ABO" to 27,
        "调教" to 28,
        "骨科" to 29,
        "诱受" to 30,
        "年下攻" to 31,
        "强强" to 32,
        "甜漫" to 33,
        "短漫" to 34,
        "女王受" to 35,
        "健气受" to 36,
        "架空" to 37,
    ),
)

class StatusFilter : PairFilter(
    "状态",
    listOf(
        "全部" to 0,
        "连载" to 1,
        "完结" to 2,
    ),
)

class SortFilter : PairFilter(
    "排序",
    listOf(
        "最新上架" to 0,
        "推荐" to 1,
        "一周人气" to 2,
        "最近更新" to 3,
    ),
)
