package eu.kanade.tachiyomi.extension.ja.nukibooks

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

class ListFilter : Filter.Select<String>("List", LISTS.map { it.first }.toTypedArray()) {
    val path get() = LISTS[state].second
}

fun getFilters() = FilterList(
    Filter.Header("A text search leaves the list out"),
    ListFilter(),
)

// The site's lists, Popular first
private val LISTS = listOf(
    "人気 TOP100" to "articles/rank",
    "トレンド TOP100" to "articles/trend",
    "エロ漫画" to "categories/4",
    "エロ同人誌" to "categories/2",
    "アーティストCG" to "categories/1",
    "画像集" to "categories/6",
)
