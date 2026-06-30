package eu.kanade.tachiyomi.extension.th.ecchidoujin

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale
import keiyoushi.annotation.Source

@Source
abstract class EcchiDoujin : MangaThemesia() {
    override val mangaUrlDirectory = "/doujin"
    override val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("th"))
}
