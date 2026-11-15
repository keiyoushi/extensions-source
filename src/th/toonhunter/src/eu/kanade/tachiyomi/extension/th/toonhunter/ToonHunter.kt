package eu.kanade.tachiyomi.extension.th.toonhunter

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source

@Source
abstract class ToonHunter : MangaThemesia() {
    override val datePattern = "d MMMM yyyy"
}
