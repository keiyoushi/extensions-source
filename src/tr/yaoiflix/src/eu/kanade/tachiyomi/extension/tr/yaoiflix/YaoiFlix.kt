package eu.kanade.tachiyomi.extension.tr.yaoiflix

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.network.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale

class YaoiFlix : Madara(
    "Yaoi Flix",
    "https://yaoiflix.kim",
    "tr",
    SimpleDateFormat("MMMM dd, yyyy", Locale("tr")),
) {
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()

    override val useNewChapterEndpoint = true

    override val useLoadMoreRequest = LoadMoreStrategy.Never
}
