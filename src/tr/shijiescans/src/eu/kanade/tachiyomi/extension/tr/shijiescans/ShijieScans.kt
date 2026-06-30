package eu.kanade.tachiyomi.extension.tr.shijiescans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale
import keiyoushi.annotation.Source

@Source
abstract class ShijieScans : MangaThemesia() {
    override val mangaUrlDirectory = "/seri"
    override val dateFormat = SimpleDateFormat("MMM d, yyy", Locale("tr"))
}
