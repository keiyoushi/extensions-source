package eu.kanade.tachiyomi.extension.th.ntrmanga

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale
import keiyoushi.annotation.Source

@Source
abstract class NTRManga : MangaThemesia() {
    override val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("th"))
}
