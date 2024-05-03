package eu.kanade.tachiyomi.extension.es.topcomicporno

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class TopComicPorno : Madara(
    "TopComicPorno",
    "https://topcomicporno.com",
    "es",
    dateFormat = SimpleDateFormat("MMM dd, yy", Locale("es")),
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true
}
