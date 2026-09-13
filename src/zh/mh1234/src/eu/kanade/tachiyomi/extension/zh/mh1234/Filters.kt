package eu.kanade.tachiyomi.extension.zh.mh1234

import eu.kanade.tachiyomi.source.model.Filter

class GenreFilter(genres: Map<String, String>) :
    SelectFilter(
        "题材",
        genres.map { (name, id) -> Pair(name, id) }.toTypedArray(),
    )

class StatusFilter :
    SelectFilter(
        "状态",
        arrayOf(
            Pair("全部", "0"),
            Pair("连载", "1"),
            Pair("完结", "2"),
        ),
    )

class SortFilter :
    SelectFilter(
        "排序",
        arrayOf(
            Pair("最新", "id"),
            Pair("热门", "hits"),
            Pair("更新", "addtime"),
        ),
    )

abstract class SelectFilter(name: String, val options: Array<Pair<String, String>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val selected get() = options[state]
}
