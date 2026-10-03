package eu.kanade.tachiyomi.extension.ja.coronaex

import eu.kanade.tachiyomi.source.model.Filter

class GenreFilter :
    SelectFilter(
        "Genres",
        arrayOf(
            Pair("少女マンガ", "41114554925057"),
            Pair("青年マンガ", "41114574733314"),
            Pair("少年マンガ", "41114587152387"),
            Pair("4コママンガ", "41114611564548"),
            Pair("Celicaコミックス", "233327015772195"),
            Pair("女性マンガ", "234901641199652"),
            Pair("コロナ・コミックス", "235983585656869"),
            Pair("完結", "235983604236326"),
            Pair("特別短期連載", "235983620751399"),
            Pair("アニメ化", "235983640330280"),
            Pair("オリジナル作品", "235983659499561"),
            Pair("恋愛", "235983673786410"),
            Pair("ファンタジー", "235983689498667"),
            Pair("異世界・転生", "235983706292268"),
            Pair("VRゲーム", "235983724527661"),
            Pair("ほのぼの", "235983737339950"),
            Pair("ハーレム", "235983751118895"),
            Pair("グルメ", "235983762554928"),
            Pair("学園・青春", "235983773859889"),
            Pair("悪役令嬢", "235983791898674"),
            Pair("ホラー・ミステリー", "235983807610931"),
            Pair("歴史・時代", "235983822307380"),
            Pair("バトル・アクション", "236305616191542"),
        ),
    )

open class SelectFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    val value: String
        get() = vals[state].second
}
