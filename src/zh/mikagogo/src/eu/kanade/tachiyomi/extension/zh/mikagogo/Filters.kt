package eu.kanade.tachiyomi.extension.zh.mikagogo

import eu.kanade.tachiyomi.source.model.Filter

open class SelectFilter(name: String, private val options: List<Pair<String, String>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val selected get() = options[state].second
}

class CategoryFilter :
    SelectFilter(
        "分类",
        listOf(
            "全部" to "0",
            "插画精选" to "7",
            "P站画师" to "3",
            "P站美图" to "2",
            "图集画册" to "8",
            "动漫壁纸" to "5",
            "电脑壁纸" to "6",
            "插画" to "4",
            "COSPLAY" to "12",
            "动漫快讯" to "9",
            "动漫下载" to "617",
            "周边" to "387",
            "未分类" to "1",
        ),
    )

class SortFilter :
    SelectFilter(
        "排序",
        listOf(
            "发布时间" to "date",
            "评论最多" to "comment_count",
            "浏览数量" to "views",
            "点赞最多" to "zan",
        ),
    )
