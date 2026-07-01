package eu.kanade.tachiyomi.extension.en.epicmanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source

@Source
abstract class EpicManga : Madara() {
    override val useNewChapterEndpoint = true
}
