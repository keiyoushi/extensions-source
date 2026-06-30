package eu.kanade.tachiyomi.extension.tr.arcurafansub

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class ArcuraFansub : MangaThemesia() {
    override val mangaUrlDirectory = "/seri"
    override val dateFormat = SimpleDateFormat("MMMM d, yyy", Locale("tr"))
}
