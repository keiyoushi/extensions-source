package eu.kanade.tachiyomi.extension.ja.pdftoshokan

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

class SortFilter :
    UriPartFilter(
        "並び替え",
        arrayOf(
            "人気" to "popular",
            "今月の人気" to "month",
            "今週の人気" to "week",
            "新着" to "",
        ),
    )

class CategoryFilter :
    UriPartFilter(
        "カテゴリー",
        arrayOf(
            "すべて" to "",
            "青年" to "青年",
            "少年" to "少年",
            "女性" to "女性",
            "少女" to "少女",
            "TL" to "tl",
            "BL" to "bl",
            "R18" to "r18",
            "小説" to "小説-novel",
            "小説 - BL" to "小説-novel/bl-小説",
            "小説 - アダルト" to "小説-novel/アダルト-小説",
            "小説 - 恋愛" to "小説-novel/恋愛-小説",
            "小説 - 異世界" to "小説-novel/異世界-小説",
            "4コマ" to "4コマ",
            "SF・ファンタジー" to "sfファンタジー",
            "お色気" to "お色気",
            "ギャグ" to "ギャグ",
            "ギャンブル" to "ギャンブル",
            "サッカー" to "サッカー",
            "サブカル" to "サブカル",
            "スポーツ" to "スポーツ",
            "ナイトビジネス" to "ナイトビジネス",
            "バトル" to "バトル",
            "ヒューマンドラマ" to "ヒューマンドラマ",
            "ファミリー" to "ファミリー",
            "ホラー" to "ホラー",
            "ミステリー" to "ミステリー",
            "ヤンキー" to "ヤンキー",
            "ラブコメ" to "ラブコメ",
            "恋愛" to "恋愛",
            "冒険" to "冒険",
            "動物" to "動物",
            "学園" to "学園",
            "悪役令嬢" to "悪役令嬢",
            "戦記・軍事" to "戦記軍事",
            "政治" to "政治",
            "料理" to "料理",
            "歴史" to "歴史",
            "異世界" to "異世界",
            "百合" to "百合",
            "社会派" to "社会派",
            "芸能界" to "芸能界",
            "萌え" to "萌え",
            "車・バイク" to "車バイク",
            "野球" to "野球",
        ),
    )
