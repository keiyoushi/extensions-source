package eu.kanade.tachiyomi.extension.ja.mangarawbest

import eu.kanade.tachiyomi.source.model.Filter

open class SelectFilter(name: String, private val options: Array<Pair<String, String>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val value get() = options[state].second
}

class SortFilter :
    SelectFilter(
        "並び替え",
        arrayOf(
            "最多視聴" to "-views",
            "更新日" to "-updated_at",
            "作成日" to "-created_at",
            "最古" to "created_at",
            "視聴回数(日)" to "-views_day",
            "視聴回数(週)" to "-views_week",
            "A-Z" to "name",
            "Z-A" to "-name",
        ),
    )

class StatusFilter : SelectFilter("状態", arrayOf("すべて" to "2,1", "進行中" to "2", "完了" to "1"))
