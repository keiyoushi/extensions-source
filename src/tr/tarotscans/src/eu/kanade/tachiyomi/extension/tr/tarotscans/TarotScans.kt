package eu.kanade.tachiyomi.extension.tr.tarotscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale
import keiyoushi.annotation.Source

@Source
abstract class TarotScans : MangaThemesia() {
    override val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("tr"))
}
