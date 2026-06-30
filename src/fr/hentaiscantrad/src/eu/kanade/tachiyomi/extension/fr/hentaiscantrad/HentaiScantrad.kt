package eu.kanade.tachiyomi.extension.fr.hentaiscantrad

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class HentaiScantrad : Madara() {
    override val dateFormat = SimpleDateFormat("d MMMM, yyyy", Locale.FRENCH)
    override val mangaDetailsSelectorStatus = "div.summary-heading:contains(État) + .summary-content"
}
