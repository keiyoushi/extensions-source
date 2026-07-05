package eu.kanade.tachiyomi.extension.pt.hentaitokyo

import eu.kanade.tachiyomi.multisrc.gattsu.Gattsu
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.seconds

@Source
abstract class HentaiTokyo : Gattsu() {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1, 2.seconds)
        .build()
}
