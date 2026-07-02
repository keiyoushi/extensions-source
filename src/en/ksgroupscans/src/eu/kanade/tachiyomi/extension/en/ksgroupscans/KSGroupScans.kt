package eu.kanade.tachiyomi.extension.en.ksgroupscans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source

@Source
abstract class KSGroupScans : Madara() {
    override val useNewChapterEndpoint = true
}
