package eu.kanade.tachiyomi.extension.ja.piecomics

import eu.kanade.tachiyomi.source.model.Filter

open class SelectFilter(name: String, private val options: Array<Pair<String, String>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val value get() = options[state].second
}

class StatusFilter :
    SelectFilter(
        "状態",
        arrayOf(
            "すべて" to "",
            "連載中" to "serialized",
            "完結" to "completed",
            "単行本化" to "published",
        ),
    )

class OrderFilter : SelectFilter("並び順", arrayOf("降順" to "", "昇順" to "asc"))
