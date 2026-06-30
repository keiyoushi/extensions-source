package eu.kanade.tachiyomi.extension.es.raikiscan

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale
import keiyoushi.annotation.Source

@Source
abstract class RaikiScan : MangaThemesia() {
    override val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("es"))
}
