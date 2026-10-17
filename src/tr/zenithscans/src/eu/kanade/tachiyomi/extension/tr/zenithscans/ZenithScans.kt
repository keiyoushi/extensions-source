package eu.kanade.tachiyomi.extension.tr.zenithscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class ZenithScans : MangaThemesia() {
    override val dateFormat = SimpleDateFormat("MMM d, yyy", Locale("tr"))
}
