package eu.kanade.tachiyomi.extension.ja.bookwalkerjp

import eu.kanade.tachiyomi.source.model.Filter

class SearchFilter :
    SelectFilter(
        "Search by",
        arrayOf(
            "電子書籍一覧" to "0",
            "話・連載" to "1",
        ),
    )

class SortFilter :
    SelectFilter(
        "Sort by",
        arrayOf(
            "人気順" to "rank",
            "適合順" to "score",
            "リリース順" to "release",
            "タイトル順" to "title",
            "読み放題新着順" to "sbsc_start_desc",
            "読み放題終了間近順" to "sbsc_end",
        ),
    )

class LibraryFilter : Filter.CheckBox("購入済み")

open class SelectFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    val value: String
        get() = vals[state].second
}
