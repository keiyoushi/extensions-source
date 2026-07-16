package eu.kanade.tachiyomi.extension.ja.mangafive

import eu.kanade.tachiyomi.source.model.Filter

open class SelectFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    val value: String
        get() = vals[state].second
}

class SortFilter :
    SelectFilter(
        "Sort by",
        arrayOf(
            "最新から" to "desc",
            "古い順" to "asc",
        ),
    )

class GenreFilter :
    SelectFilter(
        "Genres",
        arrayOf(
            "All" to "",
            "オリジナル作品" to "オリジナル作品",
            "妖怪ウォッチシリーズ" to "妖怪ウォッチシリーズ",
            "レベルファイブ作品" to "レベルファイブ作品",
        ),
    )
