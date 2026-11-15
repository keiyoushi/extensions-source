package eu.kanade.tachiyomi.extension.ja.zenon

import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer
import eu.kanade.tachiyomi.multisrc.gigaviewer.ReadableProduct
import keiyoushi.annotation.Source

@Source
abstract class Zenon : GigaViewer() {
    override val seriesListIds = listOf(
        "12207421983695504181",
        "12207421983695504182",
        "12207421983695504186",
        "12207421983695504189",
        "12207421983695504190",
        "12207421983695504198",
        "12207421983695504201",
        "10834108156697286365",
        "10834108156697286367",
        "10834108156697286369",
    )

    // Closed chapters are reported as free here, only their missing status gives them away
    override fun ReadableProduct.isUnavailable(): Boolean = purchaseInfo.unavailable || status == null

    override fun getFilterOptions() = listOf(
        "連載作品" to seriesListIds,
        "読切作品" to listOf("10834108156697286370"),
        "漫画賞" to listOf("14079602755177733270"),
        "アングラ・麻薬・ドラッグ" to listOf("Genre:2550912965876210064"),
        "怪異・ホラー・ミステリー" to listOf("Genre:2550912965876210087"),
        "サスペンス" to listOf("Genre:2550912965876210090"),
        "いじめ・復讐" to listOf("Genre:2550912965876210101"),
        "お仕事" to listOf("Genre:2550912965876210110"),
        "医療" to listOf("Genre:2550912965876210117"),
        "スポーツ" to listOf("Genre:2550912965876210128"),
        "文化系スポ根" to listOf("Genre:2550912965876210140"),
        "グルメ" to listOf("Genre:2550912965876210143"),
        "日常" to listOf("Genre:2550912965876210150"),
        "ラブコメ" to listOf("Genre:2550912965876210156"),
        "バトル" to listOf("Genre:2550912965876210160"),
        "推し" to listOf("Genre:2550912965876210168"),
        "ヒューマンドラマ" to listOf("Genre:2550912965876210181"),
        "男性向け異世界" to listOf("Genre:2550912965876210184"),
        "女性向け異世界" to listOf("Genre:2550912965876210188"),
        "男性向け歴史" to listOf("Genre:2550912965876210190"),
        "女性向け歴史" to listOf("Genre:2550912965876210194"),
        "大人向け恋愛" to listOf("Genre:2550912965876210204"),
        "若年向け恋愛" to listOf("Genre:2550912965876210217"),
    )
}
