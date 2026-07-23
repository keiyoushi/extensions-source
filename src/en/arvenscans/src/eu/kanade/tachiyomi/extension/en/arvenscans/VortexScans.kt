package eu.kanade.tachiyomi.extension.en.arvenscans

import eu.kanade.tachiyomi.multisrc.iken.Iken
import keiyoushi.annotation.Source

@Source
abstract class VortexScans : Iken() {
    override val useChaptersApi = true
}
