package eu.kanade.tachiyomi.extension.th.ecchidoujin

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class EcchiDoujin : MangaThemesia() {
    override val mangaUrlDirectory = "/doujin"
    override val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("th"))
}
