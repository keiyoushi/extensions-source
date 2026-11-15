package eu.kanade.tachiyomi.extension.ja.piccoma

import eu.kanade.tachiyomi.source.model.Filter

class RankingFilter :
    SelectFilter(
        "ランキング",
        arrayOf(
            Pair("(マンガ) 総合", "K/P/0"),
            Pair("(マンガ) ファンタジー", "K/P/2"),
            Pair("(マンガ) 恋愛", "K/P/1"),
            Pair("(マンガ) アクション", "K/P/5"),
            Pair("(マンガ) ドラマ", "K/P/3"),
            Pair("(マンガ) ホラー・ミステリー", "K/P/7"),
            Pair("(マンガ) 裏社会・アングラ", "K/P/9"),
            Pair("(マンガ) スポーツ", "K/P/6"),
            Pair("(マンガ) グルメ", "K/P/10"),
            Pair("(マンガ) 日常", "K/P/4"),
            Pair("(マンガ) 雑誌", "K/P/16"),
            Pair("(マンガ) TL", "K/P/13"),
            Pair("(マンガ) BL", "K/P/14"),
            Pair("(Smartoon) All", "S/P/0"),
            Pair("(Smartoon) ファンタジー", "S/P/2"),
            Pair("(Smartoon) 恋愛", "S/P/1"),
            Pair("(Smartoon) アクション", "S/P/5"),
            Pair("(Smartoon) ドラマ", "S/P/3"),
            Pair("(Smartoon) ホラー・ミステリー", "S/P/7"),
            Pair("(Smartoon) 裏社会・アングラ", "S/P/9"),
            Pair("(Smartoon) スポーツ", "S/P/6"),
            Pair("(Smartoon) グルメ", "S/P/10"),
            Pair("(Smartoon) 日常", "S/P/4"),
            Pair("(Smartoon) TL", "S/P/13"),
            Pair("(Smartoon) BL", "S/P/14"),
            Pair("(ノベル) 総合", "N/P/0"),
            Pair("(ノベル) ファンタジー", "N/P/2"),
            Pair("(ノベル) 恋愛", "N/P/1"),
            Pair("(ノベル) ドラマ", "N/P/3"),
            Pair("(ノベル) ホラー・ミステリー", "N/P/7"),
            Pair("(ノベル) TL", "N/P/13"),
            Pair("(ノベル) BL", "N/P/14"),
        ),
    )

open class SelectFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    val value: String
        get() = vals[state].second
}
