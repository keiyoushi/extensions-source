package eu.kanade.tachiyomi.extension.id.doujinku

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source

@Source
abstract class Doujinku : MangaThemesia() {
    override val datePattern = "d MMMM yyyy"
}
