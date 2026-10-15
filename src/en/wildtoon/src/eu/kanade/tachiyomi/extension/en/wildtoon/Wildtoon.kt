package eu.kanade.tachiyomi.extension.en.wildtoon

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source

@Source
abstract class Wildtoon : MangaThemesia() {
    override val mangaUrlDirectory = "/webtoon"
}
