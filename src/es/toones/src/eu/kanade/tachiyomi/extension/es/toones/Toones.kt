package eu.kanade.tachiyomi.extension.es.toones

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class Toones : Madara(
    "Toon-es",
    "https://toon-es.com",
    "es",
    SimpleDateFormat("MMM dd, yyyy", Locale("es")),
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true
}
