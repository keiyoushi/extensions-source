package eu.kanade.tachiyomi.extension.ja.mangarawink

import eu.kanade.tachiyomi.source.model.Filter

open class SelectFilter(
    name: String,
    val param: String,
    private val options: List<Pair<String, String>>,
    state: Int = 0,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray(), state) {
    val value get() = options[state].second
}

class GenreFilter : SelectFilter("ジャンル", "genre", listOf("全てのジャンル" to "") + GENRES.map { it to it })

class StatusFilter : SelectFilter("状態", "status", listOf("全て" to "", "連載中" to "0", "完結済み" to "1"))

class SortFilter :
    SelectFilter(
        "並び替え",
        "sort",
        listOf(
            "最終更新" to "-1",
            "最新" to "15",
            "トップ" to "10",
            "トップ月別" to "11",
            "トップ週間" to "12",
            "トップ日別" to "13",
            "フォロー" to "20",
            "章" to "30",
        ),
        state = 2,
    )

// Most series carry Japanese genres, which the site's menu lacks; its English genres that list nothing are left out
private val GENRES = listOf(
    "コメディ",
    "ファンタジー",
    "アクション",
    "ロマンス",
    "ドラマ",
    "青年",
    "少年",
    "冒険",
    "ギャグ・コメディ",
    "SF・ファンタジー",
    "アドベンチャー",
    "アクション・アドベンチャー",
    "ハーレム",
    "エッチ",
    "学園",
    "くらし・生活",
    "超自然的な",
    "ホラー",
    "フルカラー",
    "人生のひとこま",
    "学校生活",
    "神秘",
    "スポーツ",
    "成熟した",
    "異世界",
    "歴史的",
    "高校生",
    "バトル",
    "アニメ化",
    "メディア化",
    "Action",
    "Adventure",
    "Business",
    "Comedy",
    "Cooking",
    "Drama",
    "Ecchi",
    "Fantasy",
    "Harem",
    "Horror",
    "Isekai",
    "Mecha",
    "Mystery",
    "Romance",
    "School",
    "Seinen",
)
