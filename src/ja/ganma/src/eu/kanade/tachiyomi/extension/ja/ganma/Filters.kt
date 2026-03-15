package eu.kanade.tachiyomi.extension.ja.ganma

import eu.kanade.tachiyomi.source.model.Filter

class CategoryFilter :
    SelectFilter(
        "連載作品",
        arrayOf(
            Pair("月曜日", "MONDAY"),
            Pair("火曜日", "TUESDAY"),
            Pair("水曜日", "WEDNESDAY"),
            Pair("木曜日", "THURSDAY"),
            Pair("金曜日", "FRIDAY"),
            Pair("土曜日", "SATURDAY"),
            Pair("日曜日", "SUNDAY"),
            Pair("完結", "finished"),
        ),
    )

open class SelectFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    val value: String
        get() = vals[state].second
}
