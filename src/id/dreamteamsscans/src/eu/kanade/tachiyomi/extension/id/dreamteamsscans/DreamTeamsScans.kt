package eu.kanade.tachiyomi.extension.id.dreamteamsscans

import eu.kanade.tachiyomi.multisrc.loneseal.LoneSeal
import eu.kanade.tachiyomi.multisrc.loneseal.UrlLayout
import keiyoushi.annotation.Source

@Source
abstract class DreamTeamsScans : LoneSeal() {
    override val urlLayout = UrlLayout.LEGACY_ROOT
    override val includeChapterTitle = true
    override val includeSeriesTagFilter = true
    override val overloadedGenres = super.overloadedGenres + setOf("yaoi")
}
