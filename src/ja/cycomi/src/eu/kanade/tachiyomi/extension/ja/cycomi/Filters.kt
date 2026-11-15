package eu.kanade.tachiyomi.extension.ja.cycomi

import eu.kanade.tachiyomi.source.model.Filter

class CategoryFilter :
    SelectFilter(
        "Category",
        arrayOf(
            Pair("月曜日", "1"),
            Pair("火曜日", "2"),
            Pair("水曜日", "3"),
            Pair("木曜日", "4"),
            Pair("金曜日", "5"),
            Pair("土曜日", "6"),
            Pair("日曜日", "0"),
            Pair("他", "7"),
        ),
    )

open class SelectFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    val value: String
        get() = vals[state].second
}
