package eu.kanade.tachiyomi.extension.en.sanascans

import eu.kanade.tachiyomi.multisrc.iken.Iken
import keiyoushi.annotation.Source

@Source
abstract class SanaScans : Iken() {
    override val perPage = 30
    override val sortPagesByFilename = true
}
