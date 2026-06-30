package eu.kanade.tachiyomi.extension.tr.zenithscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale
import keiyoushi.annotation.Source

@Source
abstract class ZenithScans : MangaThemesia() {
    override val dateFormat = SimpleDateFormat("MMM d, yyy", Locale("tr"))
}
