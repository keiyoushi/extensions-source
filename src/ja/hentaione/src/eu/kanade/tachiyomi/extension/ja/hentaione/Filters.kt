package eu.kanade.tachiyomi.extension.ja.hentaione

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

class ListFilter : Filter.Select<String>("List", LISTS.map { it.first }.toTypedArray()) {
    val path get() = LISTS[state].second
    val isRanking get() = path.startsWith("/articles/rank")
}

class SortFilter : Filter.Select<String>("Sort", arrayOf("新着順", "人気順")) {
    val popular get() = state == 1
}

fun getFilters() = FilterList(
    Filter.Header("A text search leaves the list out"),
    ListFilter(),
    Filter.Header("The sort is for a text search or a category"),
    SortFilter(),
)

// The site's lists, Popular first
private val LISTS = listOf(
    "日間ランキング" to "/articles/rank?t=daily",
    "週間ランキング" to "/articles/rank?t=weekly",
    "月間ランキング" to "/articles/rank?t=monthly",
    "全期間ランキング" to "/articles/rank?t=all_time",
    "アーティストCG" to "/categories/1",
    "エロ同人誌" to "/categories/2",
    "エロ漫画" to "/categories/4",
    "画像集" to "/categories/6",
)
