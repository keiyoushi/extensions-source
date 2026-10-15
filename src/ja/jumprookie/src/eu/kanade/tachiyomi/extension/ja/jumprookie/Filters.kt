package eu.kanade.tachiyomi.extension.ja.jumprookie

import eu.kanade.tachiyomi.source.model.Filter

class GenreFilter :
    SelectFilter(
        "Genres",
        arrayOf(
            "TOP" to "",
            "バトル" to "1",
            "ファンタジー" to "2",
            "学園・スポーツ" to "3",
            "ラブコメ" to "4",
            "コメディ・ギャグ" to "6",
            "ミステリー・ホラー" to "7",
            "その他" to "8",
        ),
    )

open class SelectFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    val value: String
        get() = vals[state].second
}
