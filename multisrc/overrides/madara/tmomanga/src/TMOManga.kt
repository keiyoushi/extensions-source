package eu.kanade.tachiyomi.extension.es.atlantisscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class TMOManga : Madara(
    "TMO Manga",
    "https://tmomanga.com",
    "es",
) {
    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(2, 1, TimeUnit.SECONDS)
        .build()
}
