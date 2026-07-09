package eu.kanade.tachiyomi.extension.pt.hipercool

import eu.kanade.tachiyomi.multisrc.hiper.Hiper
import eu.kanade.tachiyomi.network.GET
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Hipercool : Hiper() {

    private var cookieFetched = false

    private val acceptHeaders = headersBuilder()
        .set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        .build()

    override val client = network.client.newBuilder()
        // Fetch baseUrl with accept headers which populates a needed cookie.
        .addInterceptor { chain ->
            if (!cookieFetched) {
                network.client.newCall(GET(baseUrl, acceptHeaders)).execute().close()
                cookieFetched = true
            }
            chain.proceed(chain.request())
        }
        .rateLimit(3, 1.seconds)
        .build()
}
