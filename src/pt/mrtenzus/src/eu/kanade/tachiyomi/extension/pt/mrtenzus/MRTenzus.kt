package eu.kanade.tachiyomi.extension.pt.mrtenzus

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

class MRTenzus :
    Madara(
        "MR Tenzus",
        "https://mrtenzus.com",
        "pt-BR",
        SimpleDateFormat("MM/dd/yyyy", Locale.ROOT),
    ) {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(3, 1.seconds)
        .build()

    override val useNewChapterEndpoint = true

    override val useLoadMoreRequest = LoadMoreStrategy.Always
}
