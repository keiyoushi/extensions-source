package eu.kanade.tachiyomi.extension.en.decadencescans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source

@Source
abstract class DecadenceScans : Madara() {
    override val useNewChapterEndpoint: Boolean = true
}
