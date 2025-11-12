package eu.kanade.tachiyomi.extension.id.natsu

import eu.kanade.tachiyomi.multisrc.natsuid.Natsuid
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient

class Natsu : Natsuid(
    "Natsu",
    "id",
    "https://natsu.tv",
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(4)
        .build()
}
