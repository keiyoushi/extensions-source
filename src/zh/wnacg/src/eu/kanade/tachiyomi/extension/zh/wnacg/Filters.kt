package eu.kanade.tachiyomi.extension.zh.wnacg

import eu.kanade.tachiyomi.source.model.Filter

class TagFilter : Filter.Text(name = "标签")

class SortFilter :
    UriPartFilter(
        "排序",
        arrayOf(
            "最新" to "create_time_DESC",
            "今日热门" to "heat_day_DESC",
            "本周热门" to "heat_week_DESC",
            "本月热门" to "heat_month_DESC",
            "今年热门" to "heat_year_DESC",
        ),
    )

class RankingFilter :
    UriPartFilter(
        "排行",
        arrayOf(
            "本周" to "week",
            "今日" to "day",
            "本月" to "month",
            "今年" to "year",
        ),
    )

class CategoryFilter(extraCategories: List<Pair<String, String>>) :
    UriPartFilter(
        "分类",
        CATEGORIES + extraCategories.map { (name, id) -> name to "albums-index-page-%d-cate-$id.html" },
    )

private val CATEGORIES = arrayOf(
    "" to "",
    "更新" to "albums-index-page-%d.html",
    "同人志" to "albums-index-page-%d-cate-5.html",
    "同人志-汉化" to "albums-index-page-%d-cate-1.html",
    "同人志-日语" to "albums-index-page-%d-cate-12.html",
    "同人志-English（英语）" to "albums-index-page-%d-cate-16.html",
    "同人志-CG书籍" to "albums-index-page-%d-cate-2.html",
    "写真&Cosplay" to "albums-index-page-%d-cate-3.html",
    "单行本" to "albums-index-page-%d-cate-6.html",
    "单行本-汉化" to "albums-index-page-%d-cate-9.html",
    "单行本-English（英语）" to "albums-index-page-%d-cate-17.html",
    "单行本-日语" to "albums-index-page-%d-cate-13.html",
    "杂志&短篇" to "albums-index-page-%d-cate-7.html",
    "杂志&短篇-汉语" to "albums-index-page-%d-cate-10.html",
    "杂志&短篇-日语" to "albums-index-page-%d-cate-14.html",
    "杂志&短篇-English（英语）" to "albums-index-page-%d-cate-18.html",
    "3D&漫画" to "albums-index-page-%d-cate-22.html",
    "3D&漫画-汉语" to "albums-index-page-%d-cate-23.html",
    "3D&漫画-其他" to "albums-index-page-%d-cate-24.html",
    "AI图集" to "albums-index-page-%d-cate-37.html",
    "未分類相冊" to "albums-index-page-%d-cate-0.html",
)

private val categoryIdRegex = Regex("""-cate-(\d+)\.html""")

fun unlistedCategoryId(url: String) = categoryIdRegex.find(url)?.groupValues?.get(1)
    ?.takeUnless { id -> CATEGORIES.any { "-cate-$id." in it.second } }

open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}
