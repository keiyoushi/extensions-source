package eu.kanade.tachiyomi.extension.ja.soraraw

import eu.kanade.tachiyomi.source.model.Filter

class ListFilter :
    Filter.Select<String>(
        "一覧",
        LISTS.map { it.first }.toTypedArray(),
    ) {
    val path get() = LISTS[state].second
}

private val LISTS = listOf(
    "今週の人気" to "top/last7Days.json",
    "今月の人気" to "top/last30Days.json",
    "急上昇" to "top/rising.json",
    "殿堂入り" to "top/lifetime.json",
    "新着" to "newest",
    "連載中" to "on-going",
    "完結" to "complete",
)

class Genre(val name: String, val slug: String, val ids: Set<Int>, val total: Int)

class GenreFilter(private val genres: List<Genre>) :
    Filter.Select<String>(
        "ジャンル",
        (listOf("すべて") + genres.map { "${it.name} (${it.total})" }).toTypedArray(),
    ) {
    val selected get() = genres.getOrNull(state - 1)
}
