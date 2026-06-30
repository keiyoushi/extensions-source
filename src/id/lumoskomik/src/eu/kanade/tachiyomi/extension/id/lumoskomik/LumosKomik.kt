package eu.kanade.tachiyomi.extension.id.lumoskomik

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class LumosKomik : Madara() {
    override val dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale("en"))
    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val useNewChapterEndpoint = true

    override val mangaDetailsSelectorStatus = "div.summary-heading:contains(Status) + div.summary-content"
    override val mangaDetailsSelectorDescription = "#tab-manga-summary"
    override val mangaSubString = "komik"
}
