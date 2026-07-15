package eu.kanade.tachiyomi.extension.en.manhwamanhua

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source

@Source
abstract class ManhwaManhua : Madara() {
    override val useNewChapterEndpoint = true
    override val filterNonMangaItems = false
}
