package eu.kanade.tachiyomi.extension.es.traduccionesmoonlight

import eu.kanade.tachiyomi.multisrc.moonlighttl.MoonlightTL
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class TraduccionesMoonlight : MoonlightTL() {
    private val baseUrlHost by lazy { baseUrl.toHttpUrl().host }

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2) { it.host == baseUrlHost }
}
