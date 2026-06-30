package eu.kanade.tachiyomi.extension.id.komikdewasa

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale
import keiyoushi.annotation.Source

@Source
abstract class KomikDewasa : MangaThemesia() {
    override val mangaUrlDirectory = "/komik"
    override val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("id"))
    override val hasProjectPage = true
}
