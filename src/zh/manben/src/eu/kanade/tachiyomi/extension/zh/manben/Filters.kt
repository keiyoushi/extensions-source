package eu.kanade.tachiyomi.extension.zh.manben

import eu.kanade.tachiyomi.source.model.Filter

open class SelectFilter(name: String, private val options: Array<Pair<String, String>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val value get() = options[state].second
}

class SortFilter : SelectFilter("排序", arrayOf("热门人气" to "4", "更新时间" to "2"))

class StatusFilter : SelectFilter("状态", arrayOf("全部" to "0", "连载" to "1", "完结" to "2"))

class GenreFilter :
    SelectFilter(
        "题材",
        arrayOf(
            "全部" to "0",
            "搞笑" to "37",
            "奇幻" to "14",
            "热血" to "31",
            "悬疑" to "17",
            "格斗" to "28",
            "科幻" to "25",
            "励志" to "10",
            "校园" to "1",
            "爱情" to "26",
            "日常" to "7",
            "战争" to "12",
            "其他" to "3",
        ),
    )

class ReaderFilter : SelectFilter("读者", arrayOf("全部" to "0", "少年向" to "1", "少女向" to "2", "青年向" to "3"))

class AreaFilter : SelectFilter("地区", arrayOf("全部" to "0", "港台" to "35", "日韩" to "36", "大陆" to "37", "欧美" to "52"))
