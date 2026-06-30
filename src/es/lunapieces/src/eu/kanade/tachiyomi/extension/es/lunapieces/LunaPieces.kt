package eu.kanade.tachiyomi.extension.es.lunapieces

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale
import keiyoushi.annotation.Source

@Source
abstract class LunaPieces : MangaThemesia() {
    override val mangaUrlDirectory = "/doujinshi"
    override val dateFormat = SimpleDateFormat("d MMMM, yyyy", Locale("es"))
}
