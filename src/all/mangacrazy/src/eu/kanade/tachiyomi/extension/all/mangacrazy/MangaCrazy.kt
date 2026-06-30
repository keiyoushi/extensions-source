package eu.kanade.tachiyomi.extension.all.mangacrazy

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source

@Source
abstract class MangaCrazy : Madara() {
    override val useNewChapterEndpoint = true
}
