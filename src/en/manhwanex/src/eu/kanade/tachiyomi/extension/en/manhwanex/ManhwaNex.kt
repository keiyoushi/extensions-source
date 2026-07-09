package eu.kanade.tachiyomi.extension.en.manhwanex

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source

@Source
abstract class ManhwaNex : Madara() {
    override val useNewChapterEndpoint = true

    override val statusFilterOptions = super.statusFilterOptions + mapOf("Upcoming" to "upcoming")
}
