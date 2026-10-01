package eu.kanade.tachiyomi.extension.th.doujin69

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source

@Source
abstract class Doujin69 : MangaThemesia() {
    override val mangaUrlDirectory = "/doujin"
}
