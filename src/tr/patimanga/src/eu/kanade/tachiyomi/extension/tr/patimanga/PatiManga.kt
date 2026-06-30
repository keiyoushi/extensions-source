package eu.kanade.tachiyomi.extension.tr.patimanga

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale
import keiyoushi.annotation.Source

@Source
abstract class PatiManga : MangaThemesia() {
    override val dateFormat = SimpleDateFormat("MMMM d, yyy", Locale("tr"))
}
