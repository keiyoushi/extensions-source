package eu.kanade.tachiyomi.extension.ja.comicyours

import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer
import keiyoushi.annotation.Source

@Source
abstract class ComicYours : GigaViewer() {
    override val seriesListIds = listOf(
        "2551460909784457710",
        "2551460909784457714",
        "2551460909784457715",
    )

    override fun getFilterOptions() = listOf(
        "すべて" to seriesListIds,
        "読切作品" to listOf("2551460909784457719"),
    )
}
