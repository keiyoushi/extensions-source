package eu.kanade.tachiyomi.extension.pt.winterscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

class WinterScan : Madara(
    "Winter Scan",
    "https://winterscan.com",
    "pt-BR",
    SimpleDateFormat("dd 'de' MMMM 'de' yyyy", Locale("pt", "BR")),
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1, 2.seconds)
        .build()
}
