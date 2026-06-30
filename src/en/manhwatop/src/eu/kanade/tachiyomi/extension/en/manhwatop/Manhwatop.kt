package eu.kanade.tachiyomi.extension.en.manhwatop

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source

@Source
abstract class Manhwatop : Madara() {

    // The website does not flag the content.
    override val filterNonMangaItems = false
}
