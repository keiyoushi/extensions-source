package eu.kanade.tachiyomi.extension.ja.mangahotweb

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters() = FilterList(
    Filter.Header("Ignored when searching by keyword"),
    GenreFilter(),
)

class GenreFilter : Filter.Select<String>("ジャンル", GENRES.map { it.first }.toTypedArray()) {
    val genreId get() = GENRES[state].second
}

private val GENRES = listOf(
    "すべて" to null,
    "アクション" to "1",
    "ファンタジー" to "2",
    "グルメ" to "10",
    "ホラー・サスペンス" to "8",
    "ミステリー" to "11",
    "歴史" to "5",
    "恋愛" to "3",
    "ドラマ" to "6",
    "ギャグ・コメディ" to "7",
    "学園" to "4",
    "日常・ショート" to "12",
    "仕事・スポーツ" to "9",
)
