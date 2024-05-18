package eu.kanade.tachiyomi.extension.pt.euphoriascan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class EuphoriaScan : Madara(
    "Euphoria Scan",
    "https://euphoriascan.com",
    "pt-BR",
    dateFormat = SimpleDateFormat("dd 'de' MMMM 'de' yyyy", Locale("pt", "BR")),
) {
    override val mangaDetailsSelectorStatus = "div.summary-heading:contains(Status) + div.summary-content"

    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val useNewChapterEndpoint = true
}
