package eu.kanade.tachiyomi.extension.ja.linemanga

import eu.kanade.tachiyomi.source.model.Filter

const val DAILY_LIST = "daily_list"
const val GENDER_RANKING = "periodic/gender_ranking"
const val GENRE_LIST = "genre_list"

class CategoryFilter :
    SelectFilter(
        "Category",
        arrayOf(
            Triple("月曜日", DAILY_LIST, "2"),
            Triple("火曜日", DAILY_LIST, "3"),
            Triple("水曜日", DAILY_LIST, "4"),
            Triple("木曜日", DAILY_LIST, "5"),
            Triple("金曜日", DAILY_LIST, "6"),
            Triple("土曜日", DAILY_LIST, "7"),
            Triple("日曜日", DAILY_LIST, "1"),
            Triple("総合ランキング", GENDER_RANKING, "0"),
            Triple("少年・青年ランキング", GENDER_RANKING, "1"),
            Triple("少女・女性ランキング", GENDER_RANKING, "2"),
            Triple("バトル・アクション", GENRE_LIST, "0001"),
            Triple("ファンタジー・SF", GENRE_LIST, "0002"),
            Triple("恋愛", GENRE_LIST, "0003"),
            Triple("スポーツ", GENRE_LIST, "0004"),
            Triple("ミステリー・ホラー", GENRE_LIST, "0005"),
            Triple("裏社会・アングラ", GENRE_LIST, "0006"),
            Triple("ヒューマンドラマ", GENRE_LIST, "0007"),
            Triple("歴史・時代", GENRE_LIST, "0008"),
            Triple("コメディ・ギャグ", GENRE_LIST, "0009"),
            Triple("その他", GENRE_LIST, "ffff"),
        ),
    )

open class SelectFilter(displayName: String, private val vals: Array<Triple<String, String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    val type: String
        get() = vals[state].second

    val value: String
        get() = vals[state].third
}
