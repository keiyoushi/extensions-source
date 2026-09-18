package eu.kanade.tachiyomi.extension.ja.mechacomic

import eu.kanade.tachiyomi.source.model.Filter

open class SelectFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    val value: String
        get() = vals[state].second
}

class CompletedFilter : Filter.CheckBox("完結のみ", false)

class GenreFilter :
    SelectFilter(
        "ジャンル",
        arrayOf(
            "ジャンル未選択" to "",
            "少女漫画" to "2",
            "女性漫画" to "4",
            "少年漫画" to "1",
            "青年漫画" to "3",
            "ハーレクイン漫画" to "36",
            "TL漫画" to "6",
            "BL漫画" to "24",
            "オトナコミック" to "5",
            "レディースコミック" to "34",
        ),
    )

class SortFilter :
    SelectFilter(
        "並び替え",
        arrayOf(
            "おすすめ順" to "",
            "人気順" to "current_rank",
            "新着順" to "new_book",
            "レビュー評価が高い順" to "review_score",
            "レビュー数が多い順" to "review_count",
            "話数が多い順" to "number_desc",
            "話数が少ない順" to "number_asc",
            "続話・新刊入荷順" to "new_chapter",
            "50音順" to "syllabary",
        ),
    )
