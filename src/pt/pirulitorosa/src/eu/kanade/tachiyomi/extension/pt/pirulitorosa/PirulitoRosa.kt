package eu.kanade.tachiyomi.extension.pt.pirulitorosa

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class PirulitoRosa : Madara(
    "Pirulito Rosa",
    "https://pirulitorosa.site",
    "pt-BR",
    SimpleDateFormat("dd/MM/yyy", Locale("pt", "BR")),
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1, 2, TimeUnit.SECONDS)
        .build()

    override val popularMangaUrlSelector = "div.post-title a:last-child"
}
