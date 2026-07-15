package eu.kanade.tachiyomi.extension.tr.shijiescans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class ShijieScans : MangaThemesia() {
    override val mangaUrlDirectory = "/seri"
    override val dateFormat = SimpleDateFormat("MMM d, yyy", Locale("tr"))
}
