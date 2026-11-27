package eu.kanade.tachiyomi.extension.pt.hipercool

import eu.kanade.tachiyomi.multisrc.hiper.Hiper
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.Headers
import okhttp3.OkHttpClient

@Source
abstract class Hipercool : Hiper() {

    override fun Headers.Builder.configureHeaders(): Headers.Builder = this
        .set("x-flux-node", "G2ZsDdWhUwdU82Vw")

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addHiperAuthInterceptor()
        .rateLimit(3)
}
