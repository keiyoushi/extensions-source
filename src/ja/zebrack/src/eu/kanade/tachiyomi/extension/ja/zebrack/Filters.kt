package eu.kanade.tachiyomi.extension.ja.zebrack

import eu.kanade.tachiyomi.source.model.Filter

class CategoryFilter :
    SelectFilter(
        "Category",
        arrayOf(
            Triple("月曜日", "day", "mon"),
            Triple("火曜日", "day", "tue"),
            Triple("水曜日", "day", "wed"),
            Triple("木曜日", "day", "thu"),
            Triple("金曜日", "day", "fri"),
            Triple("土曜日", "day", "sat"),
            Triple("日曜日", "day", "sun"),
            Triple("雑誌一覧", "magazine", "magazine_list"),
            Triple("少年", "genre", "1"),
            Triple("青年", "genre", "3"),
            Triple("少女", "genre", "2"),
            Triple("女性", "genre", "4"),
            Triple("アングラ・ヤンキー", "genre", "12"),
            Triple("バトル・アクション", "genre", "5"),
            Triple("スポーツ・部活・青春", "genre", "9"),
            Triple("ヒューマンドラマ", "genre", "189"),
            Triple("ミステリー・スリル・サスペンス", "genre", "7"),
            Triple("SF・ファンタジー", "genre", "8"),
            Triple("異世界・勇者・転生", "genre", "34"),
            Triple("ラブコメ", "genre", "11"),
            Triple("ギャグ・コメディ", "genre", "6"),
            Triple("グルメ・料理", "genre", "17"),
            Triple("歴史・時代物", "genre", "190"),
            Triple("恋愛", "genre", "10"),
            Triple("ホラー・オカルト", "genre", "13"),
            Triple("関連本", "genre", "239"),
            Triple("広告掲載作品", "genre", "251"),
        ),
    )

open class SelectFilter(displayName: String, private val vals: Array<Triple<String, String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    val type: String
        get() = vals[state].second

    val value: String
        get() = vals[state].third
}
