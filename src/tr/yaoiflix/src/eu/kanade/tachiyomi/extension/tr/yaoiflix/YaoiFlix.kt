package eu.kanade.tachiyomi.extension.tr.yaoiflix

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class YaoiFlix : Madara() {
    override val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("tr"))
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()

    override val useNewChapterEndpoint = true

    override val useLoadMoreRequest = LoadMoreStrategy.Never
}
