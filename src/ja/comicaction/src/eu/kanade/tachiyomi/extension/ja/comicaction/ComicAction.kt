package eu.kanade.tachiyomi.extension.ja.comicaction

import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer
import keiyoushi.annotation.Source

@Source
abstract class ComicAction : GigaViewer() {
    override val seriesListIds = listOf(
        "14079602755641808866",
        "14079602755641808844",
        "14079602755641808862",
        "14079602755641808870",
        "14079602755641808869",
        "14079602755641808856",
        "14079602755641808865",
        "14079602755641808871",
        "10834108156759733726",
    )

    override fun getFilterOptions() = listOf(
        "連載作品" to seriesListIds,
        "漫画アクション" to listOf("10834108156759733716"),
        "読切作品" to listOf("10834108156759733722"),
        "キャンペーン" to listOf("10834108156759733723"),
    )
}
