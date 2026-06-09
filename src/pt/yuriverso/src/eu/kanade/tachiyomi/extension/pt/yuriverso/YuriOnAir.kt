package eu.kanade.tachiyomi.extension.pt.yuriverso

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

class YuriOnAir :
    Madara(
        "Yuri on Air",
        "https://yurionair.top",
        "pt-BR",
        SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")),
    ) {

    // Yuri Verso -> Yuri on Air
    override val id = 4476506734614164770

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1, 2.seconds)
        .build()
}
