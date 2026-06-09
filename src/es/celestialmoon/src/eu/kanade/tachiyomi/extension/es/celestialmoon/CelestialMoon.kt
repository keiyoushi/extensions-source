package eu.kanade.tachiyomi.extension.es.celestialmoon

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.network.rateLimit
import keiyoushi.lib.cookieinterceptor.CookieInterceptor
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

class CelestialMoon :
    MangaThemesia(
        "Celestial Moon",
        "https://celestialmoonscan.es",
        "es",
        dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("es")),
    ) {
    // ZeistManga -> MangaThemesia
    override val versionId = 2

    private val cookieInterceptor = CookieInterceptor(baseUrl.substringAfter("://"), "age_gate" to "18")

    override val client = super.client.newBuilder()
        .rateLimit(3, 1.seconds) { it.host == baseUrl.toHttpUrl().host }
        .addNetworkInterceptor(cookieInterceptor)
        .build()
}
