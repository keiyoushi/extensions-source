package eu.kanade.tachiyomi.extension.en.zinmanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source

@Source
abstract class Zinmanga : Madara() {
    override val filterNonMangaItems = false
}
