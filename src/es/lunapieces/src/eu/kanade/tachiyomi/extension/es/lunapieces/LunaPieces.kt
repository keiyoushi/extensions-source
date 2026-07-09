package eu.kanade.tachiyomi.extension.es.lunapieces

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class LunaPieces : MangaThemesia() {
    override val mangaUrlDirectory = "/doujinshi"
    override val dateFormat = SimpleDateFormat("d MMMM, yyyy", Locale("es"))
}
