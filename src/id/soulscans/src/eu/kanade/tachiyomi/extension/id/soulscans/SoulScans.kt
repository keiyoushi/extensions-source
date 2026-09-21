package eu.kanade.tachiyomi.extension.id.soulscans

import eu.kanade.tachiyomi.multisrc.loneseal.LoneSeal
import eu.kanade.tachiyomi.multisrc.loneseal.UrlLayout
import keiyoushi.annotation.Source

@Source
abstract class SoulScans : LoneSeal() {
    override val urlLayout = UrlLayout.LEGACY_COMIC
    override val includeProjectOnlyFilter = true
    override val apiUrl get() = "$baseUrl/api"
}
