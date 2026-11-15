package eu.kanade.tachiyomi.extension.ja.sugocomics

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl.Builder

open class SelectFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    val value: String
        get() = vals[state].second
}

fun Builder.addFilter(param: String, filter: SelectFilter?) = filter?.value?.takeIf(String::isNotEmpty)?.let { addQueryParameter(param, it) }

class SortFilter :
    SelectFilter(
        "Sort by",
        arrayOf(
            "Newest" to "date",
            "Oldest" to "oldest",
            "Price" to "price",
        ),
    )

class CategoryFilter :
    SelectFilter(
        "Category",
        arrayOf(
            "All" to "",
            "特集・キャンペーン" to "99",
            "少年・青年" to "1",
            "メンズコミック" to "2",
            "雑誌" to "30",
            "少女・女性" to "3",
            "ボーイズラブ" to "4",
            "ティーンズラブ" to "5",
            "ガールズラブ" to "8",
            "転生・異世界" to "10",
            "アングラ・裏社会" to "19",
            "ファンタジー" to "25",
            "日常" to "13",
            "ビジネス・ヒューマンドラマ" to "11",
            "恋愛" to "9",
            "TV・映画・アニメ・ゲーム" to "12",
            "ギャグ・コメディ" to "14",
            "スポーツ" to "18",
            "作品集・短編集" to "21",
            "サスペンス・ホラー" to "16",
            "書き下ろし" to "17",
            "4コママンガ" to "22",
            "グルメ・芸術・趣味" to "20",
            "時代・歴史・戦争" to "23",
            "芸術" to "27",
            "自然・風景・動物" to "26",
            "写真集(男性タレント)" to "29",
        ),
    )

class FreeFilter : Filter.CheckBox("Free only")
