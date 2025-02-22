package eu.kanade.tachiyomi.extension.pt.spectralscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class SpectralScan : Madara(
    "Spectral Scan",
    "https://spectralscan.xyz",
    "pt-BR",
    dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("pt", "BR")),
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true
    override val mangaDetailsSelectorStatus = "div.post-content_item:contains(Estado) > div.summary-content"
}
