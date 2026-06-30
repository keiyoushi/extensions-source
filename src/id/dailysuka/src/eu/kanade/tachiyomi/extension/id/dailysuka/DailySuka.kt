package eu.kanade.tachiyomi.extension.id.dailysuka

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source

@Source
abstract class DailySuka : MangaThemesia() {
    override val mangaUrlDirectory = "/komik"
}
