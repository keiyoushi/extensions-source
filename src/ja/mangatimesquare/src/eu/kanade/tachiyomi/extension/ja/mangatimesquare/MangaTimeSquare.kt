package eu.kanade.tachiyomi.extension.ja.mangatimesquare

import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer
import keiyoushi.annotation.Source

@Source
abstract class MangaTimeSquare : GigaViewer() {
    override val seriesListIds = listOf(
        "12207421983452274168",
        "12207421983452274175",
        "12207421983452274180",
    )

    override fun getFilterOptions() = listOf(
        "作品一覧" to seriesListIds,
        "まんがタイム" to listOf("12207421983452274168"),
        "まんがホーム" to listOf("12207421983452274175"),
        "まんがタイムオリジナル" to listOf("12207421983452274180"),
        "Extra" to listOf("12207421983452274185"),
    )
}
