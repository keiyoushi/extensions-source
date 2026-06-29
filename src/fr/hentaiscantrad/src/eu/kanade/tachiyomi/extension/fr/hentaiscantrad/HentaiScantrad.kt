package eu.kanade.tachiyomi.extension.fr.hentaiscantrad

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class HentaiScantrad : Madara("Hentai-Scantrad", "https://hentai.scantrad-vf.cc", "fr", dateFormat = SimpleDateFormat("d MMMM, yyyy", Locale.FRENCH)) {
    override val mangaDetailsSelectorStatus = "div.summary-heading:contains(État) + .summary-content"
}
