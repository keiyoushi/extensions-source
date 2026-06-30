package eu.kanade.tachiyomi.extension.fr.pornhwafr

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class PornhwaFR : MangaThemesia() {
    override val mangaUrlDirectory = "/catalogue"
    override val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.FRENCH)
    override val altNamePrefix = "Nom alternatif : "
}
