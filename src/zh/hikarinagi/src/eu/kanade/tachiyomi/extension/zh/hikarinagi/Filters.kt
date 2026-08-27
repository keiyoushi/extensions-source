package eu.kanade.tachiyomi.extension.zh.hikarinagi

import eu.kanade.tachiyomi.source.model.Filter

/** 排序方式：与站点「漫画图鉴」下拉选项一致。 */
private val SORTS = listOf(
    SortPair("更新时间", "latest_chapter_at:desc"),
    SortPair("热度", "heat:desc"),
    SortPair("收录时间", "created_at:desc"),
    SortPair("发布时间", "publication_date:desc"),
    SortPair("开始时间 旧→新", "publication_date:asc"),
    SortPair("标题", "title:asc"),
)

private data class SortPair(val name: String, val value: String)

open class SortFilter(select: Selection? = null) : Filter.Sort("排序", SORTS.map { it.name }.toTypedArray(), select) {
    val activeValue = SORTS.getOrNull(state?.index ?: 0)?.value
}

/** 类型：站点「漫画图鉴」的 genre 参数。 */
internal val GENRES = listOf(
    GenrePair("全部类型", ""),
    GenrePair("战斗热血", "battle"),
    GenrePair("悬疑推理", "mystery"),
    GenrePair("冒险", "adventure"),
    GenrePair("百合", "yuri"),
    GenrePair("战争历史", "war_history"),
    GenrePair("校园", "school"),
    GenrePair("科幻", "sci_fi"),
    GenrePair("日常", "slice_of_life"),
    GenrePair("恋爱", "romance"),
    GenrePair("搞笑", "comedy"),
    GenrePair("运动", "sports"),
    GenrePair("美食", "gourmet"),
    GenrePair("音乐", "music"),
    GenrePair("职场", "workplace"),
    GenrePair("恐怖", "horror"),
    GenrePair("奇幻", "fantasy"),
    GenrePair("治愈", "healing"),
    GenrePair("催泪", "tearjerker"),
    GenrePair("动作", "action"),
)

internal data class GenrePair(val name: String, val value: String)

open class GenreFilter(
    selectedIndex: Int = 0,
) : Filter.Select<String>(
    "类型",
    GENRES.map { it.name }.toTypedArray(),
    selectedIndex,
) {
    val activeValue: String?
        get() = GENRES.getOrNull(state ?: 0)?.value
}
