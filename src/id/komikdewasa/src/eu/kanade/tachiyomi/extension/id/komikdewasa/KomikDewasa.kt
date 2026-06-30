package eu.kanade.tachiyomi.extension.id.komikdewasa

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class KomikDewasa : MangaThemesia() {
    override val mangaUrlDirectory = "/komik"
    override val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("id"))
    override val hasProjectPage = true
}
