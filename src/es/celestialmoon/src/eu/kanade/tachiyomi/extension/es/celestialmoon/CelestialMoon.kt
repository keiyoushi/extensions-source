package eu.kanade.tachiyomi.extension.es.celestialmoon

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import keiyoushi.lib.cookieinterceptor.CookieInterceptor
import keiyoushi.network.rateLimit
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class CelestialMoon : MangaThemesia() {
    override val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("es"))
    private val baseUrlHost by lazy { baseUrl.toHttpUrl().host }

    private val cookieInterceptor = CookieInterceptor(baseUrl.substringAfter("://"), "age_gate" to "18")

    override val client = super.client.newBuilder()
        .addNetworkInterceptor(cookieInterceptor)
        .rateLimit(3, 1.seconds) { it.host == baseUrlHost }
        .build()
}
