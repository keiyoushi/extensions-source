package eu.kanade.tachiyomi.extension.ja.mangaparkpublisher

import eu.kanade.tachiyomi.source.model.Filter

class TypeFilter :
    SelectFilter(
        "Filter by",
        arrayOf(
            Triple("(ランキング) 総合", "ranking", "all"),
            Triple("(ランキング) 女性", "ranking", "woman"),
            Triple("(ランキング) 男性", "ranking", "man"),
            Triple("(ランキング) 大人向け", "ranking", "adult"),
            Triple("月曜日", "series", "mon"),
            Triple("火曜日", "series", "tue"),
            Triple("水曜日", "series", "wed"),
            Triple("木曜日", "series", "thu"),
            Triple("金曜日", "series", "fri"),
            Triple("土曜日", "series", "sat"),
            Triple("日曜日", "series", "sun"),
            Triple("完", "series", "end"),
        ),
    )

open class SelectFilter(displayName: String, private val vals: Array<Triple<String, String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    val type: String
        get() = vals[state].second

    val value: String
        get() = vals[state].third
}
