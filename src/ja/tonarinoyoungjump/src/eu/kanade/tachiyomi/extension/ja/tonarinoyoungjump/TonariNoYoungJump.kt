package eu.kanade.tachiyomi.extension.ja.tonarinoyoungjump

import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer
import keiyoushi.annotation.Source

@Source
abstract class TonariNoYoungJump : GigaViewer() {
    override val seriesListIds = listOf("3269632237330951493", "3269632237330951504")

    override fun getFilterOptions() = listOf(
        "連載中" to seriesListIds,
        "読切" to listOf("3269632237330951514", "3269632237330951522", "10834108156636443058"),
        "出張作品" to listOf("3269632237330951523", "3269632237330951526"),
        "アクション" to listOf("Genre:2550912966013206559"),
        "日常/コメディ" to listOf("Genre:2550912966013206575"),
        "セクシー" to listOf("Genre:2550912966013206580"),
        "ファンタジー" to listOf("Genre:2550912966013206593"),
        "グルメ" to listOf("Genre:2550912966013206600"),
        "ホラー/ミステリー" to listOf("Genre:2550912966013206611"),
        "復讐" to listOf("Genre:2550912966013206615"),
        "スポ根" to listOf("Genre:2550912966013206624"),
        "異世界" to listOf("Genre:2550912966013206635"),
    )
}
