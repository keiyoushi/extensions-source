package eu.kanade.tachiyomi.extension.fr.scantradvf

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class ScantradVF : Madara("Scantrad-VF", "https://scantrad-vf.co", "fr", SimpleDateFormat("d MMMM yyyy", Locale.FRENCH)) {
    override val filterNonMangaItems = false
    override val useNewChapterEndpoint = true
}
