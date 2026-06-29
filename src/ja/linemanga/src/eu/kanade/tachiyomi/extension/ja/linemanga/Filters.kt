package eu.kanade.tachiyomi.extension.ja.linemanga

import eu.kanade.tachiyomi.source.model.Filter

class CategoryFilter :
    SelectFilter(
        "Category",
        arrayOf(
            Triple("月曜日", "daily_list", "2"),
            Triple("火曜日", "daily_list", "3"),
            Triple("水曜日", "daily_list", "4"),
            Triple("木曜日", "daily_list", "5"),
            Triple("金曜日", "daily_list", "6"),
            Triple("土曜日", "daily_list", "7"),
            Triple("日曜日", "daily_list", "1"),
            Triple("総合ランキング", "periodic/gender_ranking", "0"),
            Triple("少年・青年ランキング", "periodic/gender_ranking", "1"),
            Triple("少女・女性ランキング", "periodic/gender_ranking", "2"),
            Triple("バトル・アクション", "genre_list", "0001"),
            Triple("ファンタジー・SF", "genre_list", "0002"),
            Triple("恋愛", "genre_list", "0003"),
            Triple("スポーツ", "genre_list", "0004"),
            Triple("ミステリー・ホラー", "genre_list", "0005"),
            Triple("裏社会・アングラ", "genre_list", "0006"),
            Triple("ヒューマンドラマ", "genre_list", "0007"),
            Triple("歴史・時代", "genre_list", "0008"),
            Triple("コメディ・ギャグ", "genre_list", "0009"),
            Triple("その他", "genre_list", "ffff"),
        ),
    )

open class SelectFilter(displayName: String, private val vals: Array<Triple<String, String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    val type: String
        get() = vals[state].second

    val value: String
        get() = vals[state].third
}
