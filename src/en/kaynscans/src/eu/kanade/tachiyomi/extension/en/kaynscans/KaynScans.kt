package eu.kanade.tachiyomi.extension.en.kaynscans

import eu.kanade.tachiyomi.multisrc.iken.Iken
import keiyoushi.annotation.Source

@Source
abstract class KaynScans : Iken() {
    override val sortPagesByFilename = true
}
