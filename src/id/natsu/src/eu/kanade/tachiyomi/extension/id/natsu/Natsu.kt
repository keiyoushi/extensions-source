package eu.kanade.tachiyomi.extension.id.natsu

import eu.kanade.tachiyomi.multisrc.natsuid.NatsuId
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient

@Source
abstract class Natsu : NatsuId() {
    override fun OkHttpClient.Builder.customizeClient() = rateLimit(4).build().newBuilder()
}
