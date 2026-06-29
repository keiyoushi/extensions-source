package eu.kanade.tachiyomi.extension.en.wuxiaworld

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source

@Source
abstract class WuxiaWorld : Madara() {
    override val mangaSubString = "novel"
    override val useNewChapterEndpoint = true
}
