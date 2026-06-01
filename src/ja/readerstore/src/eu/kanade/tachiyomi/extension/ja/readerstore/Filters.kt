package eu.kanade.tachiyomi.extension.ja.readerstore

import eu.kanade.tachiyomi.source.model.Filter

open class SelectFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    val value: String
        get() = vals[state].second
}

class SortFilter :
    SelectFilter(
        "Sort by",
        arrayOf(
            Pair("一致度順", "match"),
            Pair("人気順", "popularRank"),
            Pair("新着順", "newArrival"),
            Pair("高評価順", "reviewScore"),
            Pair("価格の安い順", "lowPrice"),
        ),
    )
