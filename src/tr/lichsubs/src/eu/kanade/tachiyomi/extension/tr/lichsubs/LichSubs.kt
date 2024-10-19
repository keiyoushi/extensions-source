package eu.kanade.tachiyomi.extension.tr.lichsubs

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale

class LichSubs : Madara(
    "Lich Subs",
    "https://lichsubs.com",
    "tr",
    dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("tr")),
) {
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()

    override val useLoadMoreRequest = LoadMoreStrategy.Never

    override val useNewChapterEndpoint = true
}
