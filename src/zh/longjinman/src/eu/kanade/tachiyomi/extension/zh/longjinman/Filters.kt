package eu.kanade.tachiyomi.extension.zh.longjinman

import eu.kanade.tachiyomi.source.model.Filter

open class SelectFilter(name: String, private val options: Array<Pair<String, String>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val value get() = options[state].second
}

class CategoryFilter :
    SelectFilter(
        "分类",
        arrayOf(
            "全部" to "all",
            "韩漫" to "韩漫",
            "日漫" to "日漫",
            "3D漫画" to "3D漫画",
            "美女" to "美女",
            "单本" to "单本",
        ),
    )

class StatusFilter : SelectFilter("进度", arrayOf("全部" to "all", "已完结" to "completed", "更新中" to "serialized"))

class SortFilter : SelectFilter("排序", arrayOf("按阅读" to "hits", "按时间" to "time"))
