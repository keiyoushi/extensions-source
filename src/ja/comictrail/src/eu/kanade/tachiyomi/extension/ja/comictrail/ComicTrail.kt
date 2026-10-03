package eu.kanade.tachiyomi.extension.ja.comictrail

import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer
import keiyoushi.annotation.Source

@Source
abstract class ComicTrail : GigaViewer() {
    override val seriesListIds = listOf(
        "13933686331794204743",
        "13933686331794204744",
        "13933686331794204745",
    )

    override fun getFilterOptions() = listOf(
        "連載作品" to seriesListIds,
        "連載中" to listOf("13933686331794204743"),
        "連載終了" to listOf("13933686331794204744"),
        "読切" to listOf("13933686331794204745"),
    )
}
