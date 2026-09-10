package eu.kanade.tachiyomi.extension.id.komikdewasaart

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source

@Source
abstract class KomikDewasaArt : MangaThemesia() {
    override val mangaUrlDirectory = "/komik"
    override val datePattern = "dd/MM/yyyy"
}
