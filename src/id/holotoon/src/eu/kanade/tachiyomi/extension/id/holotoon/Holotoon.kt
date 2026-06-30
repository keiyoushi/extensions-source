package eu.kanade.tachiyomi.extension.id.holotoon

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class Holotoon : Madara() {
    override val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH)
    override val mangaDetailsSelectorStatus = "div.summary-heading:contains(Status) + div.summary-content"

    override val mangaSubString = "komik"
}
