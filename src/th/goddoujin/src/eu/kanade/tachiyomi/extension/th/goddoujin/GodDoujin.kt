package eu.kanade.tachiyomi.extension.th.goddoujin

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale
import keiyoushi.annotation.Source

@Source
abstract class GodDoujin : MangaThemesia() {
    override val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("th"))
    override val seriesTypeSelector = ".imptdt:contains(ประเภท) a"
}
