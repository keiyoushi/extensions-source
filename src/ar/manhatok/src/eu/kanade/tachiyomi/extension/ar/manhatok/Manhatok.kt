package eu.kanade.tachiyomi.extension.ar.manhatok

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient

@Source
abstract class Manhatok : ZeistManga() {
    override fun OkHttpClient.Builder.configureClient() = rateLimit(3)
}
