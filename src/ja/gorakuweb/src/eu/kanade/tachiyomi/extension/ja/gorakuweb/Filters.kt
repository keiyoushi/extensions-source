package eu.kanade.tachiyomi.extension.ja.gorakuweb

import eu.kanade.tachiyomi.source.model.Filter

class CategoryFilter :
    SelectFilter(
        "Category",
        arrayOf(
            Triple("すべて", "series", ""),
            Triple("連載中", "series", "false"),
            Triple("連載終了", "series", "true"),
            Triple("ちょっとH", "search", "2265225572180105560"),
            Triple("サスペンス・ホラー", "search", "4436096323774752167"),
            Triple("裏社会・アングラ", "search", "5625949039609423274"),
            Triple("ヒューマンドラマ", "search", "2948889619045390126"),
            Triple("日常・グルメ", "search", "8002052417606270687"),
            Triple("学園・青春", "search", "8275313961510206169"),
            Triple("恋愛・ファンタジー", "search", "2791600463112259269"),
            Triple("動物", "search", "7606094847790899835"),
            Triple("バトル・アクション", "search", "1671211288187869228"),
        ),
    )

open class SelectFilter(displayName: String, private val vals: Array<Triple<String, String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    val type: String
        get() = vals[state].second

    val value: String
        get() = vals[state].third
}
