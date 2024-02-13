package eu.kanade.tachiyomi.extension.fr.mangascantrad

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class MangaScantrad : Madara("Manga-Scantrad", "https://manga-scantrad.io", "fr", SimpleDateFormat("d MMM yyyy", Locale.FRANCE)) {
    override val useNewChapterEndpoint: Boolean = true
}
