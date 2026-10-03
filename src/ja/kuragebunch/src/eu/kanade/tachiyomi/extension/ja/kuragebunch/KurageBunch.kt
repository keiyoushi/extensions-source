package eu.kanade.tachiyomi.extension.ja.kuragebunch

import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer
import keiyoushi.annotation.Source

@Source
abstract class KurageBunch : GigaViewer() {
    override val seriesListIds = listOf(
        "13933686331612818223",
        "316112896902234328",
        "316112896902234338",
        "316112896902234351",
    )

    override fun getFilterOptions() = listOf(
        "くらげバンチ" to seriesListIds,
        "読切" to listOf("13933686331612818248"),
        "月刊コミックバンチ" to listOf("316112896902234380", "316112896902234391"),
        "KANATA" to listOf("14079602755178190495", "14079602755178190507", "14079602755178190520"),
        "ドラマ" to listOf("Genre:3270375685434986756"),
        "グルメ" to listOf("Genre:3270375685434986819"),
        "恋愛" to listOf("Genre:3270375685434986861"),
        "エロス" to listOf("Genre:3270375685434986895"),
        "コメディ" to listOf("Genre:3270375685434986939"),
        "動物" to listOf("Genre:3270375685434986972"),
        "エッセイ・ノンフィクション" to listOf("Genre:3270375685434987001"),
        "ファンタジー" to listOf("Genre:3270375685434987045"),
        "アクション" to listOf("Genre:3270375685434987087"),
        "趣味" to listOf("Genre:3270375685434987121"),
        "日常" to listOf("Genre:3270375685434987160"),
        "ホラー" to listOf("Genre:3270375685434987197"),
        "スポーツ" to listOf("Genre:3270375685434987232"),
        "SF" to listOf("Genre:3270375685434987276"),
        "歴史" to listOf("Genre:2551460910062589035"),
    )
}
