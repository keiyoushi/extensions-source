package eu.kanade.tachiyomi.extension.pt.algodaodoce

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class AlgodaoDoce : Madara(
    "Algodão Doce",
    "https://xn--algododoce-j5a.com",
    "pt-BR",
    dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("pt", "BR")),
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true
    override val mangaDetailsSelectorStatus = "div.post-content_item:contains(Estado) > div.summary-content"
}
