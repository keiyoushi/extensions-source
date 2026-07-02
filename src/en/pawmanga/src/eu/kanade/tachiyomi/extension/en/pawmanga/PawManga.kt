package eu.kanade.tachiyomi.extension.en.pawmanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source

@Source
abstract class PawManga : Madara() {
    override val useNewChapterEndpoint = true
}
