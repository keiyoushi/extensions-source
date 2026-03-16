package eu.kanade.tachiyomi.extension.es.yurionline

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class YuriOnline :
    Madara(
        "Yuri-Online",
        "https://yuri-online.com",
        "es",
        SimpleDateFormat("MMM dd, yyyy", Locale("es")),
    ) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true
}
