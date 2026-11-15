package eu.kanade.tachiyomi.extension.ar.murim

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient

@Source
abstract class Murim : ZeistManga() {
    override fun OkHttpClient.Builder.configureClient() = rateLimit(2)

    // Missing popular
    override val supportsLatest = false
}
