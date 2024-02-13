package eu.kanade.tachiyomi.extension.pt.rainbowfairyscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class RainbowFairyScan : Madara(
    "Rainbow Fairy Scan",
    "https://rainbowfairyscan.com",
    "pt-BR",
    SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")),
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1, 2, TimeUnit.SECONDS)
        .build()

    // Page has custom link to scan website.
    override val popularMangaUrlSelector = "div.post-title a:not([target])"
}
