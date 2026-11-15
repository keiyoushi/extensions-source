package eu.kanade.tachiyomi.extension.ja.comicgardo

import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer
import keiyoushi.annotation.Source

@Source
abstract class ComicGardo : GigaViewer() {
    override val seriesListIds = listOf("10834108156664921090")

    override fun getFilterOptions() = listOf(
        "連載作品" to seriesListIds,
        "アンソロジー・読切" to listOf("2551460909910482483"),
        "連載終了作品" to listOf("10834108156664921091"),
    )
}
