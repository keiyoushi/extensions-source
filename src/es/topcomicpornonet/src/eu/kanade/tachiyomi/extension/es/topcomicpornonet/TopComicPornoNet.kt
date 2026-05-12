package eu.kanade.tachiyomi.extension.es.topcomicpornonet

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class TopComicPornoNet :
    Madara(
        "TopComicPorno.net",
        "https://topcomicporno.net",
        "es",
        dateFormat = SimpleDateFormat("MMM dd, yy", Locale("es")),
    ) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true
}
