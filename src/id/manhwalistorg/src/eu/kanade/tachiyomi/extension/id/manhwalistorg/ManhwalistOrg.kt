package eu.kanade.tachiyomi.extension.id.manhwalistorg

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale
import keiyoushi.annotation.Source

@Source
abstract class ManhwalistOrg : MangaThemesia() {
    override val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("id"))
}
