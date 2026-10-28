package eu.kanade.tachiyomi.extension.zh.jinmantiantang

import eu.kanade.tachiyomi.source.model.Filter

internal class CategoryFilter :
    SelectFilter(
        "按类型",
        listOf(
            "全部" to "",
            "其他" to "another",
            "同人" to "doujin",
            "韩漫" to "hanman",
            "美漫" to "meiman",
            "短篇" to "short",
            "单本" to "single",
            "日语" to "doujin/sub/japanese",
            "Cosplay" to "another/sub/cosplay",
            "CG图集" to "doujin/sub/CG",
        ),
    )

internal class SortFilter :
    SelectFilter(
        "排序",
        listOf(
            "最新" to "mr",
            "最多浏览" to "mv",
            "最多爱心" to "tf",
            "最多图片" to "mp",
        ),
    )

internal class TimeFilter :
    SelectFilter(
        "时间",
        listOf(
            "全部" to "a",
            "今天" to "t",
            "这周" to "w",
            "本月" to "m",
        ),
    )

internal class TypeFilter :
    SelectFilter(
        "搜索范围",
        listOf(
            "站内搜索" to "0",
            "作品" to "1",
            "作者" to "2",
            "标签" to "3",
            "登场人物" to "4",
        ),
    )

internal class TagFilter(name: String, tags: List<String>) : SelectFilter(name, listOf("全部" to "") + tags.map { it to it })

internal class FavoritesFilter : Filter.Select<String>("收藏夹", arrayOf("关闭", "启用"), 0) {
    fun isEnabled() = state == 1
}

internal open class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val value get() = options[state].second
}
