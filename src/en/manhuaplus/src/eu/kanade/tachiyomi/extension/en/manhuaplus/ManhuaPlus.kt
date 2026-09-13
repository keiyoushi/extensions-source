package eu.kanade.tachiyomi.extension.en.manhuaplus

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source

@Source
abstract class ManhuaPlus : Madara() {
    override val filterNonMangaItems = false
    override val pageListParseSelector = ".read-container img"
}
