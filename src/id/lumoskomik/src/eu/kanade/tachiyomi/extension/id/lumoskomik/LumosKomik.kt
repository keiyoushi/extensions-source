package eu.kanade.tachiyomi.extension.id.lumoskomik

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class LumosKomik : Madara(
    "LumosKomik",
    "https://lumos01.com",
    "id",
    dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale("en")),
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val useNewChapterEndpoint = true

    override val mangaDetailsSelectorStatus = "div.summary-heading:contains(Status) + div.summary-content"
    override val mangaDetailsSelectorDescription = "#tab-manga-summary"
    override val mangaSubString = "komik"
}
