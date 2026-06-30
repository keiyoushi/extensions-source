package eu.kanade.tachiyomi.extension.id.komikdewasaart

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class KomikDewasaArt : MangaThemesia() {
    override val mangaUrlDirectory = "/komik"
    override val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT)
}
