package eu.kanade.tachiyomi.extension.es.mangashiina

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale
import keiyoushi.annotation.Source

@Source
abstract class MangaMukai : MangaThemesia() {
    override val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("es"))
}
