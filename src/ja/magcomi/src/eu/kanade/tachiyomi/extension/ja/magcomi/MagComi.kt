package eu.kanade.tachiyomi.extension.ja.magcomi

import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer
import keiyoushi.annotation.Source

@Source
abstract class MagComi : GigaViewer() {
    override val seriesListIds = listOf("2550689798442777403", "2550689798442777409")

    override fun getFilterOptions() = listOf(
        "連載中" to seriesListIds,
        "読切" to listOf("2550689798442777417", "2550689798442777419", "2550689798442777427"),
        "漫画賞・他" to listOf("2550689798442777432", "2550689798442777435"),
        "完結・休止" to listOf("2550689798442777437", "2550689798442777447"),
        "ファンタジー" to listOf("Genre:3269754496757284079"),
        "SFアクション" to listOf("Genre:3269754496757284092"),
        "男性向け「異世界」" to listOf("Genre:3269754496757284093"),
        "女性向け「異世界」" to listOf("Genre:3269754496757284105"),
        "ギャグ/コメディ" to listOf("Genre:3269754496757284113"),
        "料理/グルメ" to listOf("Genre:3269754496757284121"),
        "恋愛" to listOf("Genre:3269754496757284122"),
        "BL" to listOf("Genre:3269754496757284126"),
        "百合/GL" to listOf("Genre:3269754496757284132"),
        "歴史/時代劇" to listOf("Genre:3269754496757284140"),
        "ホラー/サスペンス/ミステリー" to listOf("Genre:3269754496757284144"),
        "エッセイ/日常" to listOf("Genre:3269754496757284147"),
        "ヒューマンドラマ" to listOf("Genre:3269754496757284148"),
        "学園/職業" to listOf("Genre:3269754496757284151"),
        "自然/動物" to listOf("Genre:3269754496757284155"),
        "スポーツ" to listOf("Genre:3269754496757284157"),
    )
}
