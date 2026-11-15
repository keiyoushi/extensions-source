package eu.kanade.tachiyomi.extension.ja.feelweb

import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer
import keiyoushi.annotation.Source

@Source
abstract class FeelWeb : GigaViewer() {
    override val seriesListIds = listOf(
        "3269754496312166681",
        "3269754496312166688",
        "3269754496312166692",
        "3269754496312166700",
        "3269754496312166718",
        "3269754496312166727",
        "3269754496312166710",
    )

    override fun getFilterOptions() = listOf(
        "作品一覧" to seriesListIds,
        "FEEL YOUNG" to listOf("3269754496312166681", "3269754496312166688"),
        "マンガJam" to listOf("3269754496312166692", "3269754496312166700"),
        "onBLUE" to listOf("3269754496312166718", "3269754496312166727"),
        "SHODENSHA COMICS" to listOf("3269754496312166710"),
    )
}
