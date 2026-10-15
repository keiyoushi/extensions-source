package eu.kanade.tachiyomi.extension.es.celestialmoon

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import keiyoushi.network.addCookie
import keiyoushi.network.rateLimit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.seconds

@Source
abstract class CelestialMoon : MangaThemesia() {
    override fun OkHttpClient.Builder.configureClient() = apply {
        addCookie("age_gate" to "18")
        rateLimit(3, 1.seconds) { it.host == baseUrl.toHttpUrl().host }
    }
}
