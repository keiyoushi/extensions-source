package eu.kanade.tachiyomi.extension.es.barmanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class BarManga : Madara(
    "BarManga",
    "https://libribar.com",
    "es",
    SimpleDateFormat("dd/MM/yyyy", Locale.ROOT),
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never

    override val mangaDetailsSelectorDescription = "div.flamesummary > div.manga-excerpt"
}
