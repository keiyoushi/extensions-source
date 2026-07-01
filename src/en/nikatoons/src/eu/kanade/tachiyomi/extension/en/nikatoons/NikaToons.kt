package eu.kanade.tachiyomi.extension.en.nikatoons

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source

@Source
abstract class NikaToons : MangaThemesia() {
    override fun chapterListSelector() = "#chapterlist li.chapter-item:not(.premium)"
}
