package eu.kanade.tachiyomi.extension.ja.corocoroonline

import eu.kanade.tachiyomi.source.model.Filter

class CategoryFilter :
    SelectFilter(
        "Category",
        arrayOf(
            "月" to "mon",
            "火" to "tue",
            "水" to "wed",
            "木" to "thu",
            "金" to "fri",
            "土" to "sat",
            "日" to "sun",
            "完結" to "completed",
            "無料" to "one-shot",
            "今日の急上昇" to "2:ranking",
            "月間総合" to "3:ranking",
            "完結" to "17:tag",
            "ギャグ・コメディ" to "32:tag",
            "バトル" to "2:tag",
            "ゲーム" to "35:tag",
            "ヒューマンドラマ" to "41:tag",
            "異世界・ファンタジー" to "44:tag",
            "ホビー" to "38:tag",
            "デュエル・マスターズ" to "170:tag",
            "アニメ化" to "20:tag",
            "スポーツ" to "47:tag",
            "ベイブレード" to "125:tag",
            "ポケモン" to "140:tag",
        ),
    )

open class SelectFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    val value: String
        get() = vals[state].second
}
