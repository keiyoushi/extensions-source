package eu.kanade.tachiyomi.extension.pt.ninjascan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.minutes

@Source
abstract class NinjaScan : Madara() {
    override val dateFormat = SimpleDateFormat("dd 'de' MMMMM 'de' yyyy", Locale("pt", "BR"))
    override val client = super.client.newBuilder()
        .connectTimeout(5.minutes)
        .readTimeout(5.minutes)
        .rateLimit(2)
        .build()

    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true
}
