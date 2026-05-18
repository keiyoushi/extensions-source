package eu.kanade.tachiyomi.extension.pt.kakuseiproject

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale

class KakuseiProject :
    Madara(
        "Kakusei Project",
        "https://kakuseiproject.org",
        "pt-BR",
        SimpleDateFormat("dd/MM/yyyy", Locale.ROOT),
    ) {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(3, 1)
        .build()

    override val useNewChapterEndpoint = true

    override val useLoadMoreRequest = LoadMoreStrategy.Always
}
