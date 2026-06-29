package eu.kanade.tachiyomi.extension.en.manga18free

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source

@Source
abstract class Manga18Free : Madara() {
    override fun searchMangaNextPageSelector() = "a.nextpostslink"
}
