package eu.kanade.tachiyomi.extension.es.celestialmoon

import eu.kanade.tachiyomi.lib.cookieinterceptor.CookieInterceptor
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.network.rateLimit
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.text.SimpleDateFormat
import java.util.Locale

class CelestialMoon : MangaThemesia(
    "Celestial Moon",
    "https://celestialmoonscan.es",
    "es",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("es")),
) {
    // ZeistManga -> MangaThemesia
    override val versionId = 2

    private val cookieInterceptor = CookieInterceptor(baseUrl.substringAfter("://"), "age_gate" to "18")

    override val client = super.client.newBuilder()
        .rateLimit(baseUrl.toHttpUrl(), 3)
        .addNetworkInterceptor(cookieInterceptor)
        .build()
}
