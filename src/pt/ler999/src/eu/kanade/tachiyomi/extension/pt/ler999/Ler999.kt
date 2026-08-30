package eu.kanade.tachiyomi.extension.pt.ler999

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Ler999 : ZeistManga() {
    override fun OkHttpClient.Builder.configureClient() = rateLimit(1, 2.seconds)
}
