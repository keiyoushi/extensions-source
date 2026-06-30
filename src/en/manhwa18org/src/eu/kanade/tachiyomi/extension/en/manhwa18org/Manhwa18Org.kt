package eu.kanade.tachiyomi.extension.en.manhwa18org

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source

@Source
abstract class Manhwa18Org : Madara() {

    // The website does not flag the content.
    override val filterNonMangaItems = false
}
