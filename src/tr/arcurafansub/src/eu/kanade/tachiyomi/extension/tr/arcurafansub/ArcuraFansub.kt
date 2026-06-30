package eu.kanade.tachiyomi.extension.tr.arcurafansub

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale
import keiyoushi.annotation.Source

@Source
abstract class ArcuraFansub : MangaThemesia() {
    override val mangaUrlDirectory = "/seri"
    override val dateFormat = SimpleDateFormat("MMMM d, yyy", Locale("tr"))
}
