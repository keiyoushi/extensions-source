package eu.kanade.tachiyomi.extension.es.celestialmoon

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.lib.cookieinterceptor.CookieInterceptor
import keiyoushi.network.rateLimit
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

class CelestialMoon :
    MangaThemesia(
        "Celestial Moon",
        "https://celestialmoonscan.es",
        "es",
        dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("es")),
    ) {
    private val baseUrlHost by lazy { baseUrl.toHttpUrl().host }

    // ZeistManga -> MangaThemesia
    override val versionId = 2

    private val cookieInterceptor = CookieInterceptor(baseUrl.substringAfter("://"), "age_gate" to "18")

    override val client = super.client.newBuilder()
        .addNetworkInterceptor(cookieInterceptor)
        .rateLimit(3, 1.seconds) { it.host == baseUrlHost }
        .build()
}
