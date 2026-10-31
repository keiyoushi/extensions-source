package eu.kanade.tachiyomi.extension.zh.xipimanhua

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(name: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(name, vals.map { it.first }.toTypedArray()) {
    val value: String get() = vals[state].second
}

class SortFilter :
    UriPartFilter(
        "排序",
        arrayOf("熱門" to "popular", "更新" to "updated", "最新" to "latest"),
    )

class CategoryFilter :
    UriPartFilter(
        "地區",
        arrayOf("全部" to "", "韩漫" to "1", "国漫" to "2", "日漫" to "3"),
    )

class StatusFilter :
    UriPartFilter(
        "狀態",
        arrayOf("全部" to "", "連載中" to "ongoing", "已完結" to "completed"),
    )

// The site has no genre index; ids from its genre pages (data-genre-id)
class GenreFilter :
    UriPartFilter(
        "類型",
        arrayOf(
            "全部" to "",
            "剧情" to "3",
            "穿越" to "20",
            "玄幻" to "27",
            "大女主" to "30",
            "复仇" to "31",
            "逆袭" to "32",
            "冒险" to "38",
            "武侠" to "39",
            "动作" to "40",
            "宫斗" to "44",
            "重生" to "46",
            "异能" to "51",
            "系统" to "67",
            "大人系" to "288",
        ),
    )
