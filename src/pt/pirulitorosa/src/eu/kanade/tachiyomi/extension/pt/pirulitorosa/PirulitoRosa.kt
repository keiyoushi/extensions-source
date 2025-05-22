package eu.kanade.tachiyomi.extension.pt.pirulitorosa

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

class PirulitoRosa : Madara(
    "Pirulito Rosa",
    "https://pirulitorosa.site",
    "pt-BR",
    SimpleDateFormat("dd/MM/yyy", Locale("pt", "BR")),
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1, 2.seconds)
        .build()

    override val popularMangaUrlSelector = "div.post-title a:last-child"
}
