package eu.kanade.tachiyomi.extension.ja.honto

import eu.kanade.tachiyomi.source.model.Filter

open class SelectFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    val value: String
        get() = vals[state].second
}

class SortFilter :
    SelectFilter(
        "並び替え",
        arrayOf(
            "一致度順" to "",
            "売上順" to "-salesnum30d",
            "新刊/続刊の発売日順" to "-saledate",
            "タイトル昇順" to "+itemnamealias",
            "タイトル降順" to "-itemnamealias",
            "ユーザー評価順" to "-customerreview -customerreviewnum defaultrank",
        ),
    )

class GenreFilter :
    SelectFilter(
        "ジャンル",
        arrayOf(
            "すべてのジャンル" to "",
            "小説・文学" to "29001000000",
            "文庫" to "29002000000",
            "新書・選書・ブックレット" to "29003000000",
            "ライトノベル" to "29037000000",
            "漫画・コミック" to "29004000000",
            "男性向けコミック" to "29004150000",
            "女性向けコミック" to "29004160000",
            "ちょいH（男性向け）" to "29004130000",
            "メンズラブ" to "29004140000",
            "エッセイ・自伝・ノンフィクション" to "29005000000",
            "暮らし・実用" to "29006000000",
            "経済・ビジネス" to "29007000000",
            "社会・時事・政治・行政" to "29008000000",
            "コンピュータ・IT・情報科学" to "29009000000",
            "旅行・地図" to "29010000000",
            "芸術・アート" to "29011000000",
            "エンタメ・テレビ・タレント" to "29012000000",
            "趣味・ホビー" to "29013000000",
            "ゲーム・アニメ・サブカルチャー" to "29014000000",
            "スポーツ" to "29015000000",
            "児童書・絵本" to "29016000000",
            "写真集" to "29017000000",
            "哲学・思想・宗教・心理" to "29018000000",
            "歴史・地理・民俗" to "29019000000",
            "法学・法律" to "29020000000",
            "自然科学・環境" to "29021000000",
            "技術・工学・農学" to "29022000000",
            "医学" to "29023000000",
            "資格・検定・就職" to "29024000000",
            "教育・学習参考書" to "29025000000",
            "言語・語学・辞典" to "29026000000",
            "本・読書・出版・全集" to "29027000000",
            "BL(ボーイズラブ)" to "29029000000",
            "TL(ティーンズラブ)" to "29039000000",
            "ラブロマンス" to "29028000000",
            "オトナ向け" to "29030000000",
            "雑誌" to "29031000000",
            "電子書籍（その他）" to "29099000000",
        ),
    )

class BrowserFilter : Filter.CheckBox("ブラウザ対応")
class AdultFilter : Filter.CheckBox("オトナ向け商品を含める")
