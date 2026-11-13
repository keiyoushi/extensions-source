package eu.kanade.tachiyomi.extension.ja.mangarawjp

import eu.kanade.tachiyomi.source.model.Filter

open class SelectFilter(name: String, private val options: Array<Pair<String, String>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val value get() = options[state].second
}

class SortFilter : SelectFilter("並び替え", arrayOf("最も多くのビュー" to "views", "最新" to "lastest", "A-Z" to "name"))

class GenreFilter :
    SelectFilter(
        "ジャンル",
        arrayOf(
            "すべて" to "",
            "ドラマ" to "drama",
            "ファンタジー" to "fantasy",
            "アクション" to "action",
            "メカ" to "mecha",
            "オルタナティブワールド" to "alternative-world",
            "神秘" to "mystery",
            "アダルト" to "adult",
            "ワンショット" to "one-shot",
            "アニメ" to "anime",
            "心理的" to "psychological",
            "コメディ" to "comedy",
            "ロマンス" to "romance",
            "漫画" to "comic",
            "学校生活" to "school-life",
            "同人誌" to "doujinshi",
            "SF" to "sci-fi",
            "青年" to "seinen",
            "エッチ" to "ecchi",
            "少女" to "Shoujo",
            "少女愛 (shojou-ai)" to "shojou-ai",
            "少女愛 (shoujo-ai)" to "shoujo-ai",
            "ジェンダーベンダー" to "Gender-Bender",
            "少年" to "Shounen",
            "ハーレム" to "Harem",
            "少年愛" to "shounen-ai",
            "歴史的" to "historical",
            "人生のひとこま" to "slice-of-life",
            "ホラー" to "Horror",
            "汚い" to "Smut",
            "じょうせい" to "Josei",
            "スポーツ" to "Sports",
            "ライブアクション" to "live-action",
            "超自然的な" to "supernatural",
            "マンファ" to "Manhua",
            "悲劇" to "Tragedy",
            "医学" to "Medical",
            "冒険" to "Adventure",
            "武道 (Martial-art)" to "Martial-art",
            "武道 (martial-arts)" to "martial-arts",
            "やおい" to "Yaoi",
            "成熟した" to "Mature",
            "異世界" to "Isekai",
            "魔法" to "Magic",
            "ロリコン" to "Lolicon",
            "食べ物" to "Food",
            "ゲーム" to "Game",
            "戦争" to "War",
            "エルフ" to "Elves",
            "百合" to "yuri",
            "ショタコン" to "shotacon",
            "SMARTOON" to "SMARTOON",
            "フルカラー" to "full-color",
        ),
    )
