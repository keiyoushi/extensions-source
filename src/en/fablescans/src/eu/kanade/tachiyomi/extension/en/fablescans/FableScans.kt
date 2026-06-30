package eu.kanade.tachiyomi.extension.en.fablescans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source

@Source
abstract class FableScans : MangaThemesia() {
    override val mangaUrlDirectory = "/comic"
}
