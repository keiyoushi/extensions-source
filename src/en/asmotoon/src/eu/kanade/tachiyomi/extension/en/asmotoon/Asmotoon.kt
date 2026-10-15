package eu.kanade.tachiyomi.extension.en.asmotoon

import eu.kanade.tachiyomi.multisrc.keyoapp.KeyoappV2
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Asmotoon : KeyoappV2() {

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(3, 5.seconds) { it.host == baseUrl.toHttpUrl().host }
    }
}
