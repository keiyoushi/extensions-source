package eu.kanade.tachiyomi.extension.en.manhuafastnet

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source

@Source
abstract class ManhuaFastNet : Madara() {
    override val useNewChapterEndpoint = true
}
