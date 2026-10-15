package eu.kanade.tachiyomi.extension.ja.ourfeel

import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer
import keiyoushi.annotation.Source

@Source
abstract class OurFeel : GigaViewer() {
    override val seriesListIds = listOf(
        "2550668105997034151",
        "2550668105997034166",
        "2550912965173938828",
        "2550668105997034155",
    )

    override fun getFilterOptions() = listOf(
        "作品一覧" to seriesListIds,
        "OUR FEEL作品" to listOf("2550668105997034151"),
        "SNS" to listOf("2550668105997034166"),
        "読切" to listOf("2550668105997034159"),
        "出張掲載" to listOf("2550912965173938828"),
        "終了作品" to listOf("2550668105997034155"),
    )
}
