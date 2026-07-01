package eu.kanade.tachiyomi.extension.en.whalemanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source

@Source
abstract class WhaleManga : Madara() {
    override val useNewChapterEndpoint = true
}
