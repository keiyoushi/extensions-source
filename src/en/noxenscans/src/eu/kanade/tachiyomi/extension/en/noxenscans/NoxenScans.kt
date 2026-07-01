package eu.kanade.tachiyomi.extension.en.noxenscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source

@Source
abstract class NoxenScans : MangaThemesia() {
    override fun chapterListSelector(): String = "#chapterlist li:not(:has(svg))"
}
