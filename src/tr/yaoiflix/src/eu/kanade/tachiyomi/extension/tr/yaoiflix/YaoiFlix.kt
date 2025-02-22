package eu.kanade.tachiyomi.extension.tr.yaoiflix

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale

class YaoiFlix : Madara(
    "Yaoi Flix",
    "https://yaoiflix.gay",
    "tr",
    SimpleDateFormat("MMMM dd, yyyy", Locale("tr")),
) {
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()

    override val useNewChapterEndpoint = true

    override val useLoadMoreRequest = LoadMoreStrategy.Never
}
