package eu.kanade.tachiyomi.extension.pt.hentaiseason

import eu.kanade.tachiyomi.multisrc.gattsu.Gattsu
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.seconds

@Source
abstract class HentaiSeason : Gattsu() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(1, 2.seconds)
}
