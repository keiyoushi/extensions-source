package eu.kanade.tachiyomi.extension.pt.kakuseiproject

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

class KakuseiProject :
    Madara(
        "Kakusei Project",
        "https://kakuseiproject.org",
        "pt-BR",
        SimpleDateFormat("dd/MM/yyyy", Locale.ROOT),
    ) {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(3, 1.seconds)
        .build()

    override val useNewChapterEndpoint = true

    override val useLoadMoreRequest = LoadMoreStrategy.Always
}
