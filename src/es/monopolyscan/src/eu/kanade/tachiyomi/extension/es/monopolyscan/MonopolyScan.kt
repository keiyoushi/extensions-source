package eu.kanade.tachiyomi.extension.es.monopolyscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class MonopolyScan : Madara(
    "Monopoly Scan",
    "https://monopolymanhua.com",
    "es",
    SimpleDateFormat("MMM dd, yyyy", Locale("es")),
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never

    override val useNewChapterEndpoint = true
}
