package eu.kanade.tachiyomi.extension.en.elftoon

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source

@Source
abstract class ElfToon : MangaThemesia() {

    override fun chapterListSelector() = "#chapterlist li:not(:has(.gem-price-icon))"
}
