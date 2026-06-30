package eu.kanade.tachiyomi.extension.en.shojoscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source

@Source
abstract class VioletScans : MangaThemesia() {
    override val mangaUrlDirectory = "/comics"

    override fun chapterListSelector(): String = "#chapterlist li:not(:has(svg))"
}
