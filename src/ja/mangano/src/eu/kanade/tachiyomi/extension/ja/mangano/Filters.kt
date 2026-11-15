package eu.kanade.tachiyomi.extension.ja.mangano

import eu.kanade.tachiyomi.source.model.Filter

class TagFilter :
    SelectFilter(
        "人気のタグ",
        arrayOf(
            "日常",
            "コメディ・ギャグ",
            "ファンタジー",
            "恋愛",
            "ヒューマンドラマ",
            "学園",
            "ラブコメ",
            "バトル",
            "SF",
            "ホラー",
            "BL",
            "カラー",
            "サスペンス・ミステリー",
            "異世界",
            "縦読み",
            "エッセイ",
        ),
    )

open class SelectFilter(displayName: String, private val vals: Array<String>) : Filter.Select<String>(displayName, vals) {
    val value: String
        get() = vals[state]
}
