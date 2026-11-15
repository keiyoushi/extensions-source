package eu.kanade.tachiyomi.extension.ja.kadocomi

import eu.kanade.tachiyomi.source.model.Filter

open class SelectFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    val value: String
        get() = vals[state].second
}

class SortFilter :
    SelectFilter(
        "Sort by",
        arrayOf(
            "人気順" to "popularity",
            "更新順" to "update",
        ),
    )
