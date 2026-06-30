package eu.kanade.tachiyomi.extension.en.writerscans

import eu.kanade.tachiyomi.multisrc.keyoapp.Keyoapp
import keiyoushi.annotation.Source

@Source
abstract class WriterScans : Keyoapp() {
    override fun popularMangaSelector() = "div:contains(Trending) + div .group.overflow-hidden"
}
