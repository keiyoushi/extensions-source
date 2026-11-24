package eu.kanade.tachiyomi.extension.id.komikmu

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source

@Source
abstract class Komikmu : MangaThemesia() {
    override val mangaUrlDirectory = "/komik"
    override val datePattern = "dd/MM/yyyy"
}
