package eu.kanade.tachiyomi.extension.zh.jinmantiantangmod

import eu.kanade.tachiyomi.source.model.Filter
internal class CategoryFilter(categories: List<Pair<String, String>>) : SelectFilter("按类型", categories)

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
            "全部" to "",
            "今天" to "t",
            "这周" to "w",
            "本月" to "m",
        ),
    )

internal class TypeFilter :
    SelectFilter(
        "搜索范围",
        listOf(
            "站内搜索" to "site",
            "作品" to "work",
            "作者" to "author",
            "标签" to "tag",
            "登场人物" to "character",
        ),
    )

internal class TagFilter(name: String, tags: List<String>) : SelectFilter(name, listOf("全部" to "") + tags.map { it to it })

internal class FavoritesFilter : Filter.Select<String>("收藏夹", arrayOf("关闭", "启用"), 0) {
    fun isEnabled() = state == 1
}

internal const val FAVORITE_SORT_DEFAULT = "recent"

// The favorites are searched and sorted in the app, so these values are not sent to the API
internal class FavoritesSortFilter :
    SelectFilter(
        "收藏夹排序",
        listOf(
            "最近收藏" to FAVORITE_SORT_DEFAULT,
            "最早收藏" to "oldest",
            "标题" to "title",
        ),
    )

internal open class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val value get() = options[state].second
}
