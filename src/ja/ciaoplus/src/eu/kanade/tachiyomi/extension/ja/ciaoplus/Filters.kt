package eu.kanade.tachiyomi.extension.ja.ciaoplus

import eu.kanade.tachiyomi.source.model.Filter

enum class FilterType { RANKING, GENRE }

class CategoryFilter :
    SelectFilter<FilterType>(
        "Filter by",
        arrayOf(
            Triple("(ランキング) まんが総合", FilterType.RANKING, "1"),
            Triple("(ランキング) 急上昇", FilterType.RANKING, "2"),
            Triple("(ランキング) 読切", FilterType.RANKING, "3"),
            Triple("(ランキング) ラブ", FilterType.RANKING, "4"),
            Triple("(ランキング) ホラー・ミステリー", FilterType.RANKING, "5"),
            Triple("(ランキング) ファンタジー", FilterType.RANKING, "6"),
            Triple("(ランキング) ギャグ・エッセイ", FilterType.RANKING, "7"),
            Triple("ギャグ・エッセイ", FilterType.GENRE, "1"),
            Triple("ラブ", FilterType.GENRE, "2"),
            Triple("ホラー・ミステリー", FilterType.GENRE, "3"),
            Triple("家族", FilterType.GENRE, "4"),
            Triple("青春・学園", FilterType.GENRE, "5"),
            Triple("友情", FilterType.GENRE, "6"),
            Triple("ファンタジー", FilterType.GENRE, "7"),
            Triple("ドリーム・サクセス", FilterType.GENRE, "8"),
            Triple("異世界", FilterType.GENRE, "9"),
        ),
    )

open class SelectFilter<T>(displayName: String, private val vals: Array<Triple<String, T, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    val type: T get() = vals[state].second
    val id: String get() = vals[state].third
}
