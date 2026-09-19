package eu.kanade.tachiyomi.extension.fr.perfscan

import eu.kanade.tachiyomi.multisrc.loneseal.LoneSeal
import eu.kanade.tachiyomi.multisrc.loneseal.UrlLayout
import keiyoushi.annotation.Source

@Source
abstract class PerfScan : LoneSeal() {
    override val urlLayout = UrlLayout.LEGACY_SERIES
    override val mangaUrlDirectory = "series"
    override val includeChapterTitle = true
}
