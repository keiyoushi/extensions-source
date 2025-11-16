package eu.kanade.tachiyomi.extension.id.natsu

import eu.kanade.tachiyomi.multisrc.natsuid.NatsuId
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient

class Natsu : NatsuId(
    "Natsu",
    "id",
    "https://natsu.tv",
) {
    override fun OkHttpClient.Builder.customizeClient() = rateLimit(4)
}
