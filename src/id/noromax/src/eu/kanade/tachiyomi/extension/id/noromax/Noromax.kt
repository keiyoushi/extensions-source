package eu.kanade.tachiyomi.extension.id.noromax

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source

@Source
abstract class Noromax : MangaThemesia() {

    override val hasProjectPage = true

    override val pageSelector = "div#readerarea img:not(noscript img)"
}
