package eu.kanade.tachiyomi.extension.en.mangatrend

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale
import keiyoushi.annotation.Source

@Source
abstract class MangaTrend : MangaThemesia() {
    override val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("ar"))
}
