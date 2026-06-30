package eu.kanade.tachiyomi.extension.tr.patimanga

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class PatiManga : MangaThemesia() {
    override val dateFormat = SimpleDateFormat("MMMM d, yyy", Locale("tr"))
}
