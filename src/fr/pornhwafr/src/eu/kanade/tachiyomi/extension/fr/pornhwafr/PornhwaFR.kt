package eu.kanade.tachiyomi.extension.fr.pornhwafr

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class PornhwaFR : MangaThemesia("Pornwha.fr", "https://pornhwa.fr", "fr", mangaUrlDirectory = "/catalogue", dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.FRENCH)) {
    override val altNamePrefix = "Nom alternatif : "
}
