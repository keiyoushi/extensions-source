package eu.kanade.tachiyomi.extension.es.ukiyotoon

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.text.SimpleDateFormat
import java.util.Locale

class UkiyoToon : MangaThemesia(
    "Ukiyo Toon",
    "https://ukiyotoon.com",
    "es",
    dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("es")),
) {
    override val client = super.client.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 3, 1)
        .build()
}
