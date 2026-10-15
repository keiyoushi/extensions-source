package eu.kanade.tachiyomi.extension.ja.comicseasons

import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer
import keiyoushi.annotation.Source

@Source
abstract class ComicSeasons : GigaViewer() {
    override val seriesListIds = listOf("2550912964592144518")

    override fun getFilterOptions() = listOf(
        "作品一覧" to seriesListIds,
        "原作小説試し読み" to listOf("2550912964592144524"),
        "溺愛系" to listOf("Genre:2550912964583261787"),
        "逆転系" to listOf("Genre:2550912964583261798"),
        "日常系" to listOf("Genre:2550912964583261807"),
        "ファンタジー" to listOf("Genre:2550912964583261815"),
        "BL" to listOf("Genre:2550912964583261826"),
        "イッキヨミ" to listOf("Genre:2550912964583261831"),
        "毎週更新" to listOf("Genre:2550912964583261841"),
        "完結" to listOf("Genre:2550912964583261851"),
        "メディア化" to listOf("Genre:2550912964583261853"),
        "コミカライズ" to listOf("Genre:2550912964583261855"),
        "受賞作" to listOf("Genre:2550912964583261858"),
        "話題作" to listOf("Genre:2550912964583261866"),
        "読み切り" to listOf("Genre:2550912964583261867"),
        "一途" to listOf("Genre:2550912964583261871"),
        "ヤンデレ" to listOf("Genre:2550912964583261881"),
        "片想い" to listOf("Genre:2550912964583261887"),
        "両想い" to listOf("Genre:2550912964583261894"),
        "ドロドロ" to listOf("Genre:2550912964583261897"),
        "シリアス" to listOf("Genre:2550912964583261906"),
        "復讐" to listOf("Genre:2550912964583261912"),
        "不倫" to listOf("Genre:2550912964583261921"),
        "浮気" to listOf("Genre:2550912964583261922"),
        "モラハラ" to listOf("Genre:2550912964583261931"),
        "シンデレラ" to listOf("Genre:2550912964583261933"),
        "ハイスペ" to listOf("Genre:2550912964583261939"),
        "元カレ" to listOf("Genre:2550912964583261946"),
        "イケメン" to listOf("Genre:2550912964583261948"),
        "偽装" to listOf("Genre:2550912964583261952"),
        "結婚" to listOf("Genre:2550912964583261957"),
        "和風" to listOf("Genre:2550912964583261964"),
        "再会" to listOf("Genre:2550912964583261965"),
        "ギャップ" to listOf("Genre:2550912964583261966"),
        "悪役令嬢" to listOf("Genre:2550912964583261973"),
        "ループ" to listOf("Genre:2550912964583261978"),
        "モフモフ" to listOf("Genre:2550912964583261986"),
        "家族" to listOf("Genre:2550912964583261995"),
    )
}
